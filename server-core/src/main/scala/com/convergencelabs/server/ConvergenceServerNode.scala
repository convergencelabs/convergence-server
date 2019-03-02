package com.convergencelabs.server

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.logging.log4j.LogManager

import com.convergencelabs.server.api.realtime.ConvergenceRealtimeApi
import com.convergencelabs.server.api.rest.ConvergenceRestApi
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.UserCreator
import com.convergencelabs.server.datastore.convergence.UserStore
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.ConnectedSingleDatabaseProvider
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.PooledDatabaseProvider
import com.convergencelabs.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.chat.ChatSharding
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.server.security.Roles
import com.convergencelabs.server.util.SystemOutRedirector
import com.orientechnologies.orient.core.db.ODatabaseType
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import grizzled.slf4j.Logging
import scala.collection.JavaConverters
import com.convergencelabs.server.datastore.convergence.ConfigKeys
import com.convergencelabs.server.datastore.convergence.ConfigStore
import com.convergencelabs.server.db.provision.DomainProvisioner
import com.convergencelabs.server.datastore.convergence.DomainCreator
import scala.concurrent.Future
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.server.util.concurrent.FutureUtils
import com.typesafe.config.ConfigObject
import scala.concurrent.ExecutionContext
import com.convergencelabs.server.datastore.convergence.NamespaceStore
import com.convergencelabs.server.datastore.convergence.UserFavoriteDomainStore
import com.convergencelabs.server.domain.DomainId

object ConvergenceServerNode extends Logging {

  object Roles {
    val Backend = "backend"
    val RestApi = "restApi"
    val RealtimeApi = "realtimeApi"
  }

  object Environment {
    val ServerRoles = "SERVER_ROLES"
    val ClusterSeedNodes = "CLUSTER_SEED_NODES"
    val Log4jConfigFile = "LOG4J_CONFIG_FILE"
  }

  object AkkaConfig {
    val AkkaClusterRoles = "akka.cluster.roles"
    val AkkaClusterSeedNodes = "akka.cluster.seed-nodes"
  }

  val ActorSystemName = "Convergence"

  var server: Option[ConvergenceServerNode] = None

  def main(args: Array[String]): Unit = {
    SystemOutRedirector.setOutAndErrToLog();

    scala.sys.addShutdownHook {
      logger.info("JVM Shutdown Hook Invoked, stopping services")
      this.stop()
    }

    (for {
      _ <- configureLogging()
      configFile <- getConfigFile(args)
      config <- preprocessConfig(ConfigFactory.parseFile(configFile))
      _ <- validateSeedNodes(config)
      _ <- validateRoles(config)
    } yield {
      server = Some(new ConvergenceServerNode(config).start())
    }).recover {
      case cause: Throwable =>
        logger.error("Could not start Convergence Server Node", cause)
    }
  }

  private[this] def stop(): Unit = {
    server.foreach(_.stop())
    LogManager.shutdown()
  }

  private[this] def getConfigFile(args: Array[String]): Try[File] = {
    Try {
      val options = ServerCLIConf(args)
      new File(options.config.toOption.get)
    } flatMap { configFile =>
      if (!configFile.exists()) {
        Failure(new IllegalArgumentException(s"Config file not found: ${configFile.getAbsolutePath}."))
      } else {
        info(s"Using config file: ${configFile.getAbsolutePath}")
        Success(configFile)
      }
    }
  }

  private[this] def preprocessConfig(config: Config): Try[Config] = {
    // This includes the reference.conf with the defaults.
    val preProcessed = preProcessRoles(preprocessSeedNodes(config))
    val loaded = ConfigFactory.load(preProcessed)
    Success(loaded)
  }

  private[this] def preprocessSeedNodes(configFile: Config): Config = {
    val configuredSeeds = configFile.getAnyRefList(AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      logger.debug("No seed nodes specified in the akka config. Looking for an environment variable")
      Option(System.getenv().get(Environment.ClusterSeedNodes)) match {
        case Some(seedNodesEnv) =>
          val seedNodes = seedNodesEnv.split(",").toList
          val seedsAddresses = seedNodes.map { hostname =>
            Address("akka.tcp", ConvergenceServerNode.ActorSystemName, hostname.trim, 2551).toString
          }
          configFile.withValue(AkkaConfig.AkkaClusterSeedNodes, ConfigValueFactory.fromIterable(seedsAddresses.asJava))

        case None =>
          configFile
      }
    } else {
      configFile
    }
  }

  private[this] def preProcessRoles(configFile: Config): Config = {
    val rolesInConfig = configFile.getStringList("akka.cluster.roles").asScala.toList
    if (rolesInConfig.isEmpty) {
      Option(System.getenv().get(Environment.ServerRoles)) match {
        case Some(rolesEnv) =>
          val roles = rolesEnv.split(",").toList map (_.trim)
          val updated = configFile.withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))
          updated
        case None =>
          configFile
      }
    } else {
      configFile
    }
  }

  private[this] def validateRoles(config: Config): Try[Unit] = {
    val roles = config.getStringList(ConvergenceServerNode.AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the config '${ConvergenceServerNode.AkkaConfig.AkkaClusterRoles}s', " +
          s"or the environment variable '${ConvergenceServerNode.Environment.ServerRoles}'"))

    } else {
      Success(())
    }
  }

  private[this] def validateSeedNodes(config: Config): Try[Unit] = {
    val configuredSeeds = config.getAnyRefList(ConvergenceServerNode.AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specificed in the akka config '${ConvergenceServerNode.AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${ConvergenceServerNode.Environment.ClusterSeedNodes}"))
    } else {
      Success(())
    }
  }

  def configureLogging(): Try[Unit] = {
    Try {
      Option(System.getenv().get(Environment.Log4jConfigFile)) match {
        case Some(path) =>
          info(s"${Environment.Log4jConfigFile} is set. Attempting to load logging config from: ${path}")
          val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
          val file = new File(path)
          if (file.canRead()) {
            info(s"Config file exists. Loading")
            // this will force a reconfiguration
            context.setConfigLocation(file.toURI());
          } else {
            warn("Log4j config file '${path}' does not exist. Ignoring.")
          }
        case None =>
      }
    }
  }
}

class ConvergenceServerNode(private[this] val config: Config) extends Logging {

  import ConvergenceServerNode.Roles._

  private[this] var system: Option[ActorSystem] = None
  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendNode] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None

  def start(): ConvergenceServerNode = {
    info("Convergence Server Node starting up...")

    val system = ActorSystem(ConvergenceServerNode.ActorSystemName, config)
    this.system = Some(system)

    val cluster = Cluster(system)
    this.cluster = Some(cluster)

    system.actorOf(Props(new ClusterListener(cluster)), name = "clusterListener")

    val roles = config.getStringList(ConvergenceServerNode.AkkaConfig.AkkaClusterRoles).asScala.toList
    info(s"Convergnece Server Roles: ${roles.mkString(", ")}")

    if (roles.contains(Backend)) {
      val persistenceConfig = config.getConfig("convergence.persistence")
      val dbServerConfig = persistenceConfig.getConfig("server")
      val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

      // TODO this only works is there is one ConvergenceServerNode with
      // backend. This is fine for development, which is the only place this
      // should exist, but it would be nice to do this elsewhere.
      if (convergenceDbConfig.hasPath("auto-install")) {
        if (convergenceDbConfig.getBoolean("auto-install.enabled")) {
          bootstrapConvergenceDB(config) recover {
            case cause: Exception =>
              logger.error("Could not bootstrap database", cause)
              System.exit(0)
          }
        }
      }

      val baseUri = dbServerConfig.getString("uri")
      val orientDb = new OrientDB(baseUri, OrientDBConfig.defaultConfig())

      // FIXME figure out how to set the pool size
      val convergenceDatabase = convergenceDbConfig.getString("database")
      val username = convergenceDbConfig.getString("username")
      val password = convergenceDbConfig.getString("password")

      val dbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password)
      dbProvider.connect().get

      if (config.hasPath("convergence.default-server-admin")) {
        autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
      }

      val domainStore = new DomainStore(dbProvider)
      system.actorOf(
        DomainPersistenceManagerActor.props(baseUri, domainStore),
        DomainPersistenceManagerActor.RelativePath)

      info("Role 'backend' configured on node, starting up backend.")
      val backend = new BackendNode(system, dbProvider)
      backend.start()
      this.backend = Some(backend)
    } else if (roles.contains(RestApi) || roles.contains(RealtimeApi)) {
      // TODO Re-factor This to some setting in the config
      val shards = 100
      DomainActorSharding.startProxy(system, shards)
      RealtimeModelSharding.startProxy(system, shards)
      ChatSharding.startProxy(system, shards)
      RestDomainActorSharding.startProxy(system, shards)
      ActivityActorSharding.startProxy(system, shards)
    }

    if (roles.contains(RestApi)) {
      info("Role 'restApi' configured on node, activating rest api.")
      val host = config.getString("convergence.rest.host")
      val port = config.getInt("convergence.rest.port")
      val restFrontEnd = new ConvergenceRestApi(system, host, port)
      restFrontEnd.start()
      this.rest = Some(restFrontEnd)
    }

    if (roles.contains(RealtimeApi)) {
      info("Role 'realtimeApi' configured on node, activating up realtime api.")
      val host = config.getString("convergence.realtime.host")
      val port = config.getInt("convergence.realtime.port")
      val realTimeFrontEnd = new ConvergenceRealtimeApi(system, host, port)
      realTimeFrontEnd.start()
      this.realtime = Some(realTimeFrontEnd)
    }

    this
  }

  private[this] def bootstrapConvergenceDB(config: Config): Try[Unit] = Try {
    val persistenceConfig = config.getConfig("convergence.persistence")
    val dbServerConfig = persistenceConfig.getConfig("server")
    val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

    logger.info("auto-install is configured, attempting to connect to the database to determin if the convergence database is installed.")

    val convergenceDatabase = convergenceDbConfig.getString("database")
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")
    val preRelease = convergenceDbConfig.getBoolean("auto-install.pre-release")
    val retryDelay = convergenceDbConfig.getDuration("retry-delay")

    val uri = dbServerConfig.getString("uri")
    val serverAdminUsername = dbServerConfig.getString("admin-username")
    val serverAdminPassword = dbServerConfig.getString("admin-password")

    val connectTries = Iterator.continually(attemptConnect(uri, serverAdminUsername, serverAdminPassword, retryDelay))
    val orientDb = connectTries.dropWhile(_.isEmpty).next().get

    logger.info("Checking for convergence database")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Covergence database does not exists.  Creating.")
      orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
      logger.info("Covergence database created, connecting as default admin user")

      val db = orientDb.open(convergenceDatabase, "admin", "admin")
      logger.info("Connected to convergence database.")

      logger.info("Deleting default 'reader' user.")
      db.getMetadata().getSecurity().getUser("reader").getDocument().delete()

      logger.info("Setting 'writer' user credentials.")
      val writerUser = db.getMetadata().getSecurity().getUser("writer")
      writerUser.setName(username)
      writerUser.setPassword(password)
      writerUser.save()

      logger.info("Setting 'admin' user credentials.")
      val adminUser = db.getMetadata().getSecurity().getUser("admin")
      adminUser.setName(adminUsername)
      adminUser.setPassword(adminPassword)
      adminUser.save()

      logger.info("Installing schema.")
      val dbProvider = new ConnectedSingleDatabaseProvider(db)
      val deltaHistoryStore = new DeltaHistoryStore(dbProvider)
      dbProvider.withDatabase { db =>
        val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
        schemaManager.install()
      }.map { _ =>
        logger.info("Schema installation complete")
      }.get

      
      // We may wind up doing this twice, consider refactoring.
      if (config.hasPath("convergence.default-server-admin")) {
        autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
      }
      
      bootstrapData(dbProvider, config)

      dbProvider.shutdown()
    } else {
      logger.info("Convergence database already exists.")
      orientDb.close()
    }
    ()
  }

  private[this] def bootstrapData(dbProvider: DatabaseProvider, config: Config): Try[Unit] = {
    val bootstrapConfig = config.getConfig("convergence.bootstrap")
    val defaultConfigs = bootstrapConfig.getConfig("default-configs")
    implicit val ec = this.system.get.dispatcher

    val configs = JavaConverters
      .asScalaSet(defaultConfigs.entrySet())
      .map(e => (e.getKey, e.getValue.unwrapped))
      .toMap

    val configStore = new ConfigStore(dbProvider)
    val favoriteStore = new UserFavoriteDomainStore(dbProvider)
    val domainCreator = new InlineDomainCreator(dbProvider, config, ec)
    val namespaceStore = new NamespaceStore(dbProvider)
    configStore.setConfigs(configs)
    
    val namespaces = bootstrapConfig.getList("namespaces")
    namespaces.forEach { namespaceConfig =>
      namespaceConfig match {
        case obj: ConfigObject =>
          val c = obj.toConfig()
          val id = c.getString("id")
          val displayName = c.getString("displayName")
          logger.info(s"bootstrapping namespace ${id}")
          namespaceStore.createNamespace(id, displayName, false).get
      }
    }
    
    val domains = bootstrapConfig.getList("domains")
    JavaConverters.asScalaBuffer(domains).toList.foreach { domainConfig =>
      domainConfig match {
        case obj: ConfigObject =>
          val c = obj.toConfig()
          val namespace = c.getString("namespace")
          val id = c.getString("id")
          val displayName = c.getString("displayName")
          val favorite = c.getBoolean("favorite")
          val anonymousAuth = c.getBoolean("config.anonymousAuthEnabled")

          logger.info(s"bootstrapping domain ${namespace}/${id}")
          (for {
            exists <- namespaceStore.namespaceExists(namespace)
            created <- if (!exists) {
              Failure(new IllegalArgumentException("The namespace for a bootstraped domain, must also be bootstraped"))
            } else {
              Success(())
            }
          } yield {
            val f = domainCreator.createDomain(namespace, id, displayName, anonymousAuth).get.map { _ =>
              logger.info(s"bootstrapped domain ${namespace}/${id}")
            }
            
            Await.ready(f, FiniteDuration.apply(2, TimeUnit.MINUTES))
            
            if (favorite) {
              val username = config.getString("convergence.default-server-admin.username")
              favoriteStore.addFavorite(username, DomainId(namespace, id)).get
            }
          }).get
      }
    }
    Success(())
  }

  private[this] def attemptConnect(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration): Option[OrientDB] = {
    info(s"Attempting to connect to the datatbase at uri: ${uri}")

    Try(new OrientDB(uri, adminUser, adminPassword, OrientDBConfig.defaultConfig())) match {
      case Success(orientDb) =>
        logger.info("Connected to datatbase with Server Admin")
        Some(orientDb)
      case Failure(e) =>
        logger.error(s"Unable to connect to datatbase, retrying in ${retryDelay.toMillis()}ms", e)
        Thread.sleep(retryDelay.toMillis())
        None
    }
  }

  private[this] def autoConfigureServerAdmin(dbProvider: DatabaseProvider, config: Config): Unit = {
    logger.debug("Configuring default server admin user")
    val userStore = new UserStore(dbProvider)

    val username = config.getString("username")
    val password = config.getString("password")
    userStore.userExists(username).flatMap { exists =>
      if (!exists) {
        logger.debug("Admin user does not exist, creating.")
        val userCreator = new UserCreator(dbProvider)

        val firstName = config.getString("firstName")
        val lastName = config.getString("lastName")
        val displayName = config.hasPath("displayName") match {
          case true  => config.getString("displayName")
          case false => "Server Admin"
        }
        val email = config.getString("email")

        val user = User(username, email, firstName, lastName, displayName, None)
        userCreator.createUser(user, password, Roles.Server.ServerAdmin)
      } else {
        logger.debug("Admin user exists, updating password.")
        userStore.setUserPassword(username, password)
      }
    }.recover {
      case cause: Throwable =>
        logger.error("Error creating server admin user", cause)
    }
  }

  def stop(): Unit = {
    logger.info(s"Stopping the Convergence Server Node")

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())

    logger.info(s"Leaving the cluster.")
    cluster.foreach(c => c.leave(c.selfAddress))

    system foreach { s =>
      logger.info(s"Terminating actor system.")
      s.terminate()
      Await.result(s.whenTerminated, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info(s"Actor system terminated.")
    }
  }
}

private class ClusterListener(cluster: Cluster) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member) =>
      log.debug(s"Member with role '${member.roles}' is Up: ${member.address}")
    case UnreachableMember(member) =>
      log.debug("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.debug("Member is Removed: {} after {}", member.address, previousStatus)
    case msg: MemberEvent =>
      log.debug(msg.toString)
  }
}

class InlineDomainCreator(
  provider: DatabaseProvider,
  config:   Config,
  ec:       ExecutionContext) extends DomainCreator(provider, config, ec) {
  val provisioner = new DomainProvisioner(provider, config)

  def provisionDomain(request: ProvisionDomain): Future[Unit] = {
    val ProvisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth) = request
    FutureUtils.tryToFuture(provisioner.provisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth))
  }
}

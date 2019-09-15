package com.convergencelabs.server

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.convergencelabs.server.api.realtime.ConvergenceRealtimeApi
import com.convergencelabs.server.api.rest.ConvergenceRestApi
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.convergence._
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.provision.DomainProvisioner
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.server.db.{ConnectedSingleDatabaseProvider, DatabaseProvider, PooledDatabaseProvider}
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.chat.ChatSharding
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.server.domain.{DomainActorSharding, DomainId}
import com.convergencelabs.server.security.Roles
import com.convergencelabs.server.util.SystemOutRedirector
import com.convergencelabs.server.util.concurrent.FutureUtils
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters
import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

object ConvergenceServer extends Logging {

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

  var server: Option[ConvergenceServer] = None

  def main(args: Array[String]): Unit = {
    SystemOutRedirector.setOutAndErrToLog()

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
      server = Some(new ConvergenceServer(config).start())
    }).recover {
      case cause: Throwable =>
        logger.error("Could not start Convergence Server", cause)
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
            Address("akka.tcp", ConvergenceServer.ActorSystemName, hostname.trim, 2551).toString
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
    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the config '${ConvergenceServer.AkkaConfig.AkkaClusterRoles}s', " +
          s"or the environment variable '${ConvergenceServer.Environment.ServerRoles}'"))

    } else {
      Success(())
    }
  }

  private[this] def validateSeedNodes(config: Config): Try[Unit] = {
    val configuredSeeds = config.getAnyRefList(ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specified in the akka config '${ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${ConvergenceServer.Environment.ClusterSeedNodes}"))
    } else {
      Success(())
    }
  }

  def configureLogging(configFile: Option[String] = None): Try[Unit] = {
    // Check for the environment config.
    val env: Option[String] = Option(System.getenv().get(Environment.Log4jConfigFile))

    // Take one or the other
    val config: Option[String] = (configFile orElse env) ensuring (_ => (configFile, env).zipped.isEmpty)

    Try {
      config.foreach { path =>
        info(s"${Environment.Log4jConfigFile} is set. Attempting to load logging config from: $path")
        val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
        val file = new File(path)
        if (file.canRead) {
          info(s"Config file exists. Loading")
          // this will force a reconfiguration
          context.setConfigLocation(file.toURI)
        } else {
          warn(s"Log4j config file '$path' does not exist. Ignoring.")
        }
      }
    }
  }
}

class ConvergenceServer(private[this] val config: Config) extends Logging {

  import ConvergenceServer.Roles._

  private[this] var system: Option[ActorSystem] = None
  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendNode] = None
  private[this] var orientDb: Option[OrientDB] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None

  def start(): ConvergenceServer = {
    val ver = config.getString("convergence.version")
    info(s"Convergence Server ($ver) starting up...")

    val system = ActorSystem(ConvergenceServer.ActorSystemName, config)
    this.system = Some(system)

    val cluster = Cluster(system)
    this.cluster = Some(cluster)

    system.actorOf(Props(new ClusterListener(cluster)), name = "clusterListener")

    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toList
    info(s"Convergence Server Roles: ${roles.mkString(", ")}")

    if (roles.contains(Backend)) {
      val persistenceConfig = config.getConfig("convergence.persistence")
      val dbServerConfig = persistenceConfig.getConfig("server")
      val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

      // TODO this only works is there is one ConvergenceServer with
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
      orientDb = Some(new OrientDB(baseUri, OrientDBConfig.defaultConfig()))

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

    logger.info("auto-install is configured, attempting to connect to the database to determine if the convergence database is installed.")

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

    logger.info("Checking for Convergence database")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Convergence database does not exists.  Creating.")
      orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
      logger.debug("Convergence database created, connecting as default admin user")

      val db = orientDb.open(convergenceDatabase, "admin", "admin")
      logger.info("Connected to convergence database.")

      logger.debug("Deleting default 'reader' user.")
      db.getMetadata.getSecurity.getUser("reader").getDocument.delete()

      logger.debug("Setting 'writer' user credentials.")
      val writerUser = db.getMetadata.getSecurity.getUser("writer")
      writerUser.setName(username)
      writerUser.setPassword(password)
      writerUser.save()

      logger.debug("Setting 'admin' user credentials.")
      val adminUser = db.getMetadata.getSecurity.getUser("admin")
      adminUser.setName(adminUsername)
      adminUser.setPassword(adminPassword)
      adminUser.save()

      logger.info("Installing Convergence schema.")
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
    implicit val ec: ExecutionContextExecutor = this.system.get.dispatcher

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
    namespaces.forEach {
      case obj: ConfigObject =>
        val c = obj.toConfig
        val id = c.getString("id")
        val displayName = c.getString("displayName")
        logger.info(s"bootstrapping namespace '$id'")
        namespaceStore.createNamespace(id, displayName, userNamespace = false).get
    }

    val domains = bootstrapConfig.getList("domains")
    JavaConverters.asScalaBuffer(domains).toList.foreach {
      case obj: ConfigObject =>
        val c = obj.toConfig
        val namespace = c.getString("namespace")
        val id = c.getString("id")
        val displayName = c.getString("displayName")
        val favorite = c.getBoolean("favorite")
        val anonymousAuth = c.getBoolean("config.anonymousAuthEnabled")

        logger.info(s"bootstrapping domain '$namespace/$id'")
        (for {
          exists <- namespaceStore.namespaceExists(namespace)
          created <- if (!exists) {
            Failure(new IllegalArgumentException("The namespace for a bootstrapped domain, must also be bootstraped"))
          } else {
            Success(())
          }
        } yield {
          val f = domainCreator.createDomain(namespace, id, displayName, anonymousAuth).get.map { _ =>
            logger.info(s"bootstrapped domain '$namespace/$id'")
          }

          Await.ready(f, FiniteDuration.apply(2, TimeUnit.MINUTES))

          if (favorite) {
            val username = config.getString("convergence.default-server-admin.username")
            favoriteStore.addFavorite(username, DomainId(namespace, id)).get
          }
        }).get
    }
    Success(())
  }

  private[this] def attemptConnect(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration): Option[OrientDB] = {
    info(s"Attempting to connect to the database at uri: $uri")

    Try(new OrientDB(uri, adminUser, adminPassword, OrientDBConfig.defaultConfig())) match {
      case Success(db) =>
        logger.info("Connected to database with Server Admin")
        Some(db)
      case Failure(e) =>
        logger.error(s"Unable to connect to database, retrying in ${retryDelay.toMillis}ms", e)
        Thread.sleep(retryDelay.toMillis)
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
        val displayName = if (config.hasPath("displayName")) {
          config.getString("displayName")
        } else {
          "Server Admin"
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
    logger.info(s"Stopping the Convergence Server")

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())
    this.orientDb.foreach(db => db.close())

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
                           config: Config,
                           ec: ExecutionContext) extends DomainCreator(provider, config, ec) {
  val provisioner = new DomainProvisioner(provider, config)

  def provisionDomain(request: ProvisionDomain): Future[Unit] = {
    val ProvisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth) = request
    FutureUtils.tryToFuture(provisioner.provisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth))
  }
}

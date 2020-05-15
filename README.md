<div align="center">
  <img alt="Convergence Logo" height="80" src="https://convergence.io/assets/img/convergence-logo.png" >
</div>

# Convergence Server
[![Build Status](https://travis-ci.org/convergencelabs/convergence-server.svg?branch=master)](https://travis-ci.org/convergencelabs/convergence-server)

The Convergence Server is the main server side component of the [Convergence](https://convergence.io) Realtime Collaboration Framework. Convergence enables developers to rapidly integrate realtime collaboration directly into their applications.

## Issue Reporting
The core Convergence capability is composed of multiple individual projects that are released together. To simplify things, there is a central project that is used for issues, project planning, and road mapping.  To report an issue please use the [convergence-project](https://github.com/convergencelabs/convergence-project) repository.

## Languages and Frameworks
* **[Scala](https://www.scala-lang.org/)**: The Convergence Server is developed primarily in Scala.
* **[SBT](https://www.scala-sbt.org/)**: SBT is the build tool used by the Convergence Server.
* **[Akka](https://akka.io)**: Akka is the main development framework used by the Convergence Server. Akka provides the primary ability for multiple Convergence Servers to cluster together, providing horizontal scalability, and high availability. 
* **[OrientDB](https://orientdb.org/)**: Orient DB is used as the backing database. 
* **[Google Protocol Buffers](https://developers.google.com/protocol-buffers/)**: Protocol Buffers are used as the communications protocol for realtime collaboration over Web Sockets.

## Development
The following development tools are required to build the Convergence Server:

* [Java](https://openjdk.java.net/) 11.x
* [Scala](http://www.scala-lang.org/download/) 2.12.x 
* [SBT](http://www.scala-sbt.org/) 1.3.x


The standard SBT tasks can be used to compile and test the server.

* `sbt compile`
* `sbt test`

The main entry point of the Convergence Server is the `com.convergencelabs.server.ConvergenceServer` class. This is a good place to start if you are new to the code base. 

## Binary Distribution
The Convergence Server uses the [SBT Native Packager](https://github.com/sbt/sbt-native-packager) to build its binary distribution. To stage the build run:
 
```shell script
sbt stage
```

The resultant build will be located in `target/universal/stage`.

Refer to the [SBT Native Packager Documentation](https://sbt-native-packager.readthedocs.io/en/stable/) for additional build targets.

## Convergence Dev Server
The Convergence Dev Server runs an all-in-one instance of Convergence along with an embedded OrientDB Database. The Convergence Dev Server will start up and OrientDB database and initialize it. It will also start a backend node, a rest API, and a realtime API. In order to better reflect a typical deployment, th Convergence Dev Server actually starts up three instances of the Convergence Server (cluster see, backend, and api server). These three instances are tied together using Akka clustering. By default ports 2551, 2552, and 2553 are used by the akka remoting subsystem (each port being used by one of the three ConvergenceServer instances).

By default, when the Convergence Dev Server successfully starts, it will provide two endpoints:

* Realtime API: http://localhost:8080
* Rest API: http://localhost:8081

### Running the Convergence Dev Server
The Convergence Dev Server can be run from your IDE of choice by executing the following main class:
```
com.convergencelabs.server.testkit.ConvergenceDevServer
```

### Persistent Data
By default the Convergence Dev Server will delete the OrientDB database(s) when it starts up. IF you would like to retain data between runs set the following java property:

`-Dconvergence.dev-server.persistent = true`

### Embedded Orient DB
In order to use the OrientDB web interface, the OrientDB Studio plugin must be loaded. The plugin is a dependency of the Convergence Server project but must be copied into the "target/orientdb/plugins" directory.  As a convenience, there is an SBT task available to do this. To initialize the Orient DB plugins run the following SBT Command:

```shell
sbt orientDbPlugins
```

The embedded OrientDB can be accessed at: http://localhost:2480/

The credentials `root` / `password` can be used to access the databases.

## Support
[Convergence Labs](https://convergencelabs.com) provides several different channels for support:

- Please use the [Convergence Community Forum](https://forum.convergence.io) for general and technical questions, so the whole community can benefit.
- For paid dedicated support or custom development services, [contact us](https://convergence.io/contact-sales/) directly.
- Chat with us on the [Convergence Public Slack](https://slack.convergence.io).
- Email <support@convergencelabs.com> for all other inquiries.

## License
The Convergence Server is licensed under the [GNU Public License v3](LICENSE) (GPLv3) license. Refer to the [LICENSE](LICENSE) for the specific terms and conditions of the license.

The Convergence Server is also available under a Commercial License. If you are interested in a non-open source license please contact us at [Convergence Labs](https://convergencelabs.com).

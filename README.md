# Convergence Server

<img src="docs/images/logo.png" width="100" />


This is the main repository for the Convergence Server. [Convergence](https://convergence.io) enables realtime collaboration within modern application. 

## Languages and Frameworks
The server is developed primarily in Scala and leverages [Akka](https://akka.io) as the major application framework. Akka provides the primary ability for multiple Convergence Servers to cluster together, providing horizontal scalability. [OrientDB](https://orientdb.org/) is used as the database. [Google Protocol Buffers](https://developers.google.com/protocol-buffers/) are used as the communications protocol for realtime collaboration over web sockets.

## Development Requirements
The following development tools are required to build the Convergence Server:

* [Java](https://openjdk.java.net/) 11.x
* [Scala](http://www.scala-lang.org/download/) 2.12.x 
* [SBT](http://www.scala-sbt.org/) 1.2.x
* [Docker](https://docker.com) >= 19.0

## Convergence Dev Server
The Convergence Dev Server runs an all-in-one instance of Convergence along with an embedded OrientDB Database. The Convergence Dev Server will start up and OrientDB database and initialized it. It will also start a backend node, a rest api, and a realtime API. In order to better reflect a typical deployment, th Convergence Dev Server actually starts up three instances of the Convergence Server (cluster see, backend, and api server). These three instances are tied together using Akka clustering. By default ports 2551, 2552, and 2553 are used by the akka remoting subsystem (each port being used by one of the three ConvergenceServer instances).

By default, when the Convergence Dev Server successfully starts, it will provide two endpoints:

* Realtime API: http://localhost:8080
* Rest API: http://localhost:8081

### Running the Convergence Dev Server
The Convergence Dev Server can be run from your IDE of choice by executing the `com.convergencelabs.server.testkit.ConvergenceDevServer` class.

### Embedded Orient DB
The embedded OrientDB can be accessed at: http://localhost:2480/

The credentials `root` / `password` can be used to access the database.

### Persistent Data
By default the Convergence Dev Server will delete the OrientDB database(s) when it starts up. IF you would like to retain data between runs set the following java property:

`-Dconvergence.dev-server.persistent = true`


## Docker
To build a local Docker container execute the `scripts/docker-build.sh` file.  If you are not running on a machine with bash, you  can simply execute the commands in that file to  create the Docker image.

The docker container will be named `convergence-server`.  


You can run it as follows:

```bash
docker run -p 0.0.0.0:8080-8081:8080-8081 convergence-server
```

The -p 8080:8080 links the port on the localhost to the port 8080 in the container.  If you are running on OSX or Windows, you will be running docker in a VM that has an ip address other than local host.  The container will then be running on some other IP.

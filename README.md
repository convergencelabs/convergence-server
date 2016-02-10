# convergence-server

This is the main repository for the Convergence Backend Server.  The server is developed primarily in Scala and leverages OrientDB and Akka as major frameworks.

## Development Requirements

 1. Java 8
 2. Scala 2.11.7 - http://www.scala-lang.org/download/
 3. SBT 13.8 - http://www.scala-sbt.org/
 

## Development Server

A development version of the server can be deployed for use in development of the client and / or simple application.  The test server contains an embedded database.  The test server can be build as either a folder with a shell / batch file or as a docker container.  

### Test Server Folder
The test-server folder is built using sbt-pack.

```bash
$ sbt pack
```

The server will be deployed to target/pack.

### Docker Container
You will need to install docker.  If you are on linux, everything should be straightforward.  If you are on windows or linux, you will likely need to run the following commands from the docker shell, so that the appropriate environment variable will be set.  Then run:

```bash
$ sbt docker
```

The docker container will be named "com.convergencelabs/convergence-server".  You can run it as follows:

```bash
docker run -p 8080:8080 com.convergencelabs/convergence-server
```

The -p 8080:8080 links the port on the localhost to the port 8080 in the container.  If you are running on OSX or Windows, you will be running docker in a VM that has an ip address other than local host.  The container will then be running on some other IP.



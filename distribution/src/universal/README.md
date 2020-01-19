# Convergence Server
The Convergence Server is the main server side component of the [Convergence](https://convergence.io) Realtime Collaboration Framework. Convergence enables developers to rapidly integrate realtime collaboration directly into their applications.

## Usage
```shell script
bin/convergence-server
```

## Configuration
The configuration files can be found in the `conf` directory:

```
- conf
  - convergence-server.conf     Configures the Convergence Server
  - log4j2.xml                  Configures the Convergence Server Logging
```

## Orient DB
An Orient DB 3.0.x installation is required for the Convergence Server to run. This version of Convergence was built with OrientDB 3.0.26.  

OrientDB can be downloaded here: [https://orientdb.com/download-2/](https://orientdb.com/download-2/). If you have Docker, the easiest way to get an instance of OrientDB up and running is to execute the following docker command:

```shell script
docker run --rm -d --name orientdb -p 2424:2424 -p 2480:2480 -e ORIENTDB_ROOT_PASSWORD=password orientdb
```

If you change the OrientDB root password, you will need to update the associated convergence-server.conf setting to match.


## Issue Reporting
To report an issue please use the [convergence-project](https://github.com/convergencelabs/convergence-project) repository.

## Support
[Convergence Labs](https://convergencelabs.com) provides several different channels for support:

- Please use the [Convergence Community Forum](https://forum.convergence.io) for general and technical questions, so the whole community can benefit.
- For paid dedicated support or custom development services, [contact us](https://convergence.io/contact-sales/) directly.
- Email <support@convergencelabs.com> for all other inquiries.

## License
The Convergence Server is licensed under the [GNU Public License v3](LICENSE) (GPLv3) license. Refer to the [LICENSE](LICENSE) for the specific terms and conditions of the license.

The Convergence Server is also available under a Commercial License. If you are interested in a non-open source license please contact us at [Convergence Labs](https://convergencelabs.com).

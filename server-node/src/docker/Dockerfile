FROM java:openjdk-8-jre-alpine

ADD stage /opt/convergence
RUN chmod +x /opt/convergence/bin/convergence-server-node

ADD convergence-server-application.conf /opt/convergence/config

EXPOSE 8080
EXPOSE 8081

WORKDIR /opt/convergence/

ENTRYPOINT ["/opt/convergence/bin/convergence-server-node"]

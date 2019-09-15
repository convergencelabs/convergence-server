FROM openjdk:11.0.4-jdk

RUN mkdir /etc/convergence/

ADD stage /opt/convergence
RUN chmod +x /opt/convergence/bin/convergence-server

ADD convergence-server.conf /etc/convergence/convergence-server.conf

EXPOSE 8080
EXPOSE 8081

WORKDIR /opt/convergence/

ENTRYPOINT ["/opt/convergence/bin/convergence-server"]

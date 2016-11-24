FROM java:openjdk-8-jre-alpine

# Set Up confd
ADD bin/confd-0.11.0-linux-amd64 /usr/local/bin/confd
RUN chmod +x /usr/local/bin/confd

ADD bin/boot.sh /opt/convergence/bin/boot.sh
RUN chmod +x /opt/convergence/bin/boot.sh

ADD confd /etc/confd/

ADD pack /opt/convergence
RUN chmod +x /opt/convergence/bin/server-node

# Create a place for the config to go, so confd can work
RUN mkdir /opt/convergence/config

EXPOSE 8080
EXPOSE 8081

WORKDIR /opt/convergence/

CMD ["/opt/convergence/bin/boot.sh"]
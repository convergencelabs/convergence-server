FROM java:openjdk-8-jre-alpine

ADD pack /opt/convergence
RUN chmod +x /opt/convergence/bin/server-node

# Set Up confd
ADD bin/confd-0.11.0-linux-amd64 /usr/local/bin/confd
RUN chmod +x /usr/local/bin/confd
ADD confd /etc/confd/

ADD schema /opt/convergence/schema

ADD bin/boot.sh /opt/convergence/bin/boot.sh
RUN chmod +x /opt/convergence/bin/boot.sh

EXPOSE 8080
EXPOSE 8081

WORKDIR /opt/convergence/

CMD [/usr/local/bin/confd -onetime -backend env]
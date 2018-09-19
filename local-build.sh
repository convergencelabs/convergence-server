#!/bin/bash
sbt -J-Xmx3G -J-Xss5M clean compile serverNode/stage

cp -a server-node/src/docker/ server-node/target/docker
cp -a server-node/target/universal/stage server-node/target/docker/stage

docker build -t docker.dev.int.convergencelabs.tech/convergence-server-node:latest server-node/target/docker

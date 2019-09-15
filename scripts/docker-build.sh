#!/bin/bash
sbt -J-Xmx3G -J-Xss5M compile stage

cp -a src/docker/ target/docker
cp -a target/universal/stage target/docker/stage

docker build -t convergence-server:latest target/docker

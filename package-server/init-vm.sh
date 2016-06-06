#!/bin/bash

# Java 8 source
add-apt-repository -y ppa:webupd8team/java
echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections

# Docker source
apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D
echo "deb https://apt.dockerproject.org/repo ubuntu-trusty main" > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-engine openjdk-7-jre-headless postgresql supervisor
usermod -a -G docker arjun
apt-get update
# Need the thing to accept the license
apt-get install -y oracle-java8-installer
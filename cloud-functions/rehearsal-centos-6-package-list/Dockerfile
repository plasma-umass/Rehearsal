FROM centos:6
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN yum -y install yum-utils wget
RUN wget http://download.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
RUN rpm -ivh epel-release-6-8.noarch.rpm
RUN curl --silent --location https://rpm.nodesource.com/setup_8.x | bash -
RUN yum -y install nodejs
COPY action /action

ENTRYPOINT ["/usr/bin/node", "/action/js/index.js"]
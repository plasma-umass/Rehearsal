FROM centos:6
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN yum -y install yum-utils wget
RUN wget http://download.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
RUN rpm -ivh epel-release-6-8.noarch.rpm
RUN curl --silent --location https://rpm.nodesource.com/setup_8.x | bash -
RUN curl --silent --location https://dl.yarnpkg.com/rpm/yarn.repo | tee /etc/yum.repos.d/yarn.repo
RUN yum install -y yarn nodejs
COPY action /action
RUN cd /action && yarn install

ENTRYPOINT ["/usr/bin/node", "/action/js/index.js"]
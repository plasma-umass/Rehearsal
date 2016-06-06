FROM centos:6
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN yum -y install yum-utils wget
RUN wget http://download.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
RUN rpm -ivh epel-release-6-8.noarch.rpm

ADD entrypoint.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
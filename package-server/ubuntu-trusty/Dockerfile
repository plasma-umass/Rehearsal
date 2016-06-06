FROM ubuntu:14.04
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN apt-get update
RUN apt-get install -y apt-file
RUN apt-file update
ADD entrypoint.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
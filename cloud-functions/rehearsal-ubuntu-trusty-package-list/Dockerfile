FROM ubuntu:14.04
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN apt-get update
RUN apt-get install -y curl apt-file
RUN curl -sL https://deb.nodesource.com/setup_8.x | bash -
RUN apt-get install -y nodejs
RUN apt-file update
COPY action /action

ENTRYPOINT ["/usr/bin/node", "/action/js/index.js"]
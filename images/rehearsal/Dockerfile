FROM ubuntu:18.04
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN apt-get update -q
RUN apt-get install -yq default-jre apt-transport-https curl wget unzip build-essential nodejs
RUN wget -O datalog.tar.gz https://sourceforge.net/projects/datalog/files/datalog/2.6/datalog-2.6.tar.gz/download
RUN tar xzf datalog.tar.gz
WORKDIR /datalog-2.6
RUN ./configure
RUN sed -i "s/-DHAVE_LIBREADLINE=1//g" Makefile
RUN make
RUN make install
WORKDIR /
RUN rm -rf datalog-2.6
RUN rm datalog.tar.gz
RUN apt-get install -yq z3

COPY action /action
ENTRYPOINT ["/usr/bin/node", "/action/dist/index.js"]

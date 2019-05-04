FROM ubuntu:14.04
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN apt-get update -q
RUN apt-get install -yq software-properties-common apt-transport-https curl wget
RUN curl -sL https://deb.nodesource.com/setup_8.x | bash -
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
RUN gpg --keyserver keyserver.ubuntu.com --recv-key E084DAB9
RUN gpg -a --export E084DAB9 | sudo apt-key add -
RUN apt-get update -q
COPY /jre-8u211-linux-x64.tar.gz /jre-8u211-linux-x64.tar.gz
RUN tar xzf /jre-8u211-linux-x64.tar.gz
RUN ln -s /jre1.8.0_211/bin/java /usr/local/bin/java
RUN apt-get install -yq nodejs unzip build-essential nodejs
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
RUN wget https://github.com/Z3Prover/z3/releases/download/z3-4.4.1/z3-4.4.1-x64-ubuntu-14.04.zip
RUN unzip z3-4.4.1-x64-ubuntu-14.04.zip
RUN mv /z3-4.4.1-x64-ubuntu-14.04/bin/z3 /usr/local/bin/z3
RUN rm -rf /z3-4.4.1-x64-ubuntu-14.04

COPY action /action
ENTRYPOINT ["/usr/bin/node", "/action/dist/index.js"]

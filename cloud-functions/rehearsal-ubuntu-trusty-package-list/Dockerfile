FROM ubuntu:14.04
MAINTAINER Arjun Guha <arjun@cs.umass.edu>

RUN apt-get update
RUN apt-get install -y curl apt-file
RUN curl -sL https://deb.nodesource.com/setup_8.x | bash -
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | sudo apt-key add -
RUN echo "deb https://dl.yarnpkg.com/debian/ stable main" | sudo tee /etc/apt/sources.list.d/yarn.list
RUN apt-get update
RUN apt-get install -y nodejs yarn
COPY action /action
RUN cd /action && yarn install
RUN apt-file update

ENTRYPOINT ["/usr/bin/node", "/action/js/index.js"]
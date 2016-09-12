#!/bin/bash
set -e
set -x

if [ ! -d $HOME/mydeps ]; then
  mkdir $HOME/mydeps
fi

cd $HOME/mydeps

if [ ! -x "/home/travis/mydeps/z3/bin/z3" ]; then
  echo "Fetching Z3 ..."
  ZIPFILE=z3-4.4.1-x64-ubuntu-14.04.zip
  URL=https://github.com/Z3Prover/z3/releases/download/z3-4.4.1/$ZIPFILE
  wget $URL
  unzip $ZIPFILE
  mv z3-4.4.1-x64-ubuntu-14.04 z3
fi

if [ ! -x "/home/travis/mydeps/datalog-2.5/datalog" ]; then
    echo "Fetching Datalog ..."
    wget -O datalog.tar.gz https://sourceforge.net/projects/datalog/files/datalog/2.5/datalog-2.5.tar.gz/download
    tar xzf datalog.tar.gz
    pushd datalog-2.5
    ./configure
    sed -i "s/-DHAVE_LIBREADLINE=1//g" Makefile
    make
    popd
fi

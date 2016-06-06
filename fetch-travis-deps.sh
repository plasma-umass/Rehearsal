#!/bin/bash
if [ -x "/home/travis/mydeps/z3/bin/z3" ]; then
  echo "Not fetching any dependencies"
  exit 0
fi

cd /home/travis
rm -rf mydeps
mkdir mydeps
cd mydeps

ZIPFILE=z3-4.4.1-x64-ubuntu-14.04.zip
URL=https://github.com/Z3Prover/z3/releases/download/z3-4.4.1/$ZIPFILE
wget $URL
unzip $ZIPFILE
mv z3-4.4.1-x64-ubuntu-14.04 z3

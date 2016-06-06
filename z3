#!/bin/bash
mkdir -p logs
filename=logs/`date "+%Y-%m-%dT%H-%M-%S"`.smt

if [[ -x /home/travis/mydeps/z3/bin/z3 ]]; then
  REAL_Z3=/home/travis/mydeps/z3/bin
elif [[ -x /usr/local/bin/z3 ]]; then
  REAL_Z3=/usr/local/bin/z3
elif [[ -x /usr/bin/z3 ]]; then
  REAL_Z3=/usr/bin/z3
else
  echo "Could not find z3 binary" > /dev/stderr
  exit 1
fi

tee -a $filename | $REAL_Z3 $@ | tee -a $filename

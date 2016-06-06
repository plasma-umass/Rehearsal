#!/bin/bash
mkdir -p logs
filename=logs/`date "+%Y-%m-%dT%H-%M-%S"`.smt

REAL_Z3=`which z3`
if [[ ! -x $REAL_Z3 ]]; then
  echo "Could not find z3 binary" > /dev/stderr
  exit 1
fi

tee -a $filename | $REAL_Z3 $@ | tee -a $filename

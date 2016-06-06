#!/bin/bash
mkdir -p logs
filename=logs/`date "+%Y-%m-%dT%H-%M-%S"`.datalog

REAL_DATALOG=`which datalog`$HOME/bin/datalog
if [[ ! -x $REAL_DATALOG ]]; then
  echo "Could not find datalog binary" > /dev/stderr
  exit 1
fi

tee -a $filename | $REAL_DATALOG | tee -a $filename

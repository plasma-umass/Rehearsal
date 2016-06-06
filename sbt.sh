#!/bin/bash
ARGS=$@
LOGFILE=rehearsal.log
PATH=`pwd`:$PATH
sbt -J-Xmx4G -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
  -Dorg.slf4j.simpleLogger.logFile=$LOGFILE

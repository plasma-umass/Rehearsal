#!/bin/bash
set -e
set -x
ACTION_NAME=rehearsal-ubuntu-trusty-package-list
IMAGE_NAME=arjunguha/$ACTION_NAME
docker build -t $IMAGE_NAME .
docker push $IMAGE_NAME
bx wsk action update $ACTION_NAME
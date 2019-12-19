#!/bin/bash
set -x
set -e
PROJECTID=`gcloud config get-value project`
docker build . --tag gcr.io/$PROJECTID/rehearsal
docker push gcr.io/$PROJECTID/rehearsal
gcloud beta run deploy rehearsal --image gcr.io/$PROJECTID/rehearsal --memory=2Gi

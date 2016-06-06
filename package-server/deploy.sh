#!/bin/bash
ZONE=us-central1-a
NAME=$1
gcloud docker push gcr.io/arjun-umass/pkglist-ubuntu-trusty
gcloud docker push gcr.io/arjun-umass/pkglist-centos-6

gcloud compute instances create $1 \
  --image ubuntu-14-04 \
  --machine-type g1-small \
  --zone $ZONE \
  --tags allow-unprivileged-http,ssh

gcloud compute copy-files target/scala-2.11/package-listing-assembly-0.1.jar \
  arjun@$1:/home/arjun/package-listing.jar \
  --zone $ZONE


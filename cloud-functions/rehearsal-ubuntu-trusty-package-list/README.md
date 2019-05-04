This project assumes you are using Google Cloud Run.

First, set the `$PROJECTID` environment variable to your Google Cloud Platform
project ID. Then run:

```
docker build . --tag gcr.io/$PROJECTID/rehearsal-ubuntu-trusty-package-list
docker push gcr.io/$PROJECTID/rehearsal-ubuntu-trusty-package-list
gcloud beta run deploy rehearsal-ubuntu-trusty-package-list --image gcr.io/$PROJECTID/rehearsal-ubuntu-trusty-package-list

```



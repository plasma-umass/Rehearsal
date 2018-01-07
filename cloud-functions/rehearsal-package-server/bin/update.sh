#!/bin/bash
set -x
set -e

./bin/zip.sh
bx wsk action update rehearsal-package-server --kind=nodejs:8 rehearsal-package-server.zip
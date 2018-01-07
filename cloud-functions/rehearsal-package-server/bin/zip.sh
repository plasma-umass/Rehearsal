#!/bin/bash
set -x
set -e
zip -q -r rehearsal-package-server.zip node_modules dist package.json

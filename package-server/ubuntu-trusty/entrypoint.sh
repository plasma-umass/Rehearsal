#!/bin/bash
apt-file -F list $1 | cut -f 2 -d " "
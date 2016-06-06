# Rehearsal Package Server

This directory has the source code for a simple web service that
returns file listings for packages. (It currently supports CentOS 6 and
Ubuntu 14.04.)

The high-level design is as follows:

- The web service is written in Scala and built using SBT.
- It uses Docker to run several operating systems and query their package
  repositories.
- Since queries can take some time, it caches results in a Postgres database.

The web service is presently deployed on Google Compute Engine, though
it can be run on any machine if Postgres, Docker, and SBT are installed.

There are several scripts in this directory that attempt to automate
deployment to GCE. But, they have are hardcoded to work with a particular
Google account.
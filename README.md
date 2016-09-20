[![Build Status](https://travis-ci.org/plasma-umass/Rehearsal.svg?branch=master)](https://travis-ci.org/plasma-umass/Rehearsal)

# Rehearsal: A Configuration Verification Tool for Puppet

See the [Rehearsal website](http://plasma-umass.github.io/Rehearsal/home/) for a demo.

## Automated Installation with Vagrant.

We've provided a [Vagrantfile](https://www.vagrantup.com) that creates a
VirtualBox VM with everything needed to run Rehearsal and its benchmarks.
After installing Vagrant, from the root directory of this repository, run
the following command to create the virtual machine:

    vagrant up --provider virtualbox

After the virtual machine is built, SSH into the machine and build Rehearsal:

    vagrant ssh
    cd /vagrant
    sbt compile

Next, run the benchmarks:

    cd results
    make all

This command will produce the files `sizes.pdf`,
`determinism.pdf`, and `idempotence.pdf` which correspond to
Figures 12(a), 12(b), and 12(c) in the accepted version of the paper.

## Manual Installation

We've used both Ubuntu Linux and Mac OS X to develop Rehearsal. It should be
straightforward to install Rehearsal on these systems. Rehearsal *may* work on
Windows, but we do not claim that it will.

### Prerequisites

1. Oracle JDK 8

2. [Microsoft Z3 Theorem Prover 4.4.1](https://github.com/Z3Prover/z3/releases/tag/z3-4.4.1)

   After installation, place the `z3` executable in your `PATH`.

   **NOTE:** We've had issues the version of Z3 that is in the Ubuntu 16.04
   repositories. We suggest downloading Z3 from the link above.

3. A Datalog implementation. Rehearsal will work with
   [John Ramsdell's Datalog](http://datalog.sourceforge.net) (version 2.5 or
   higher). With a little more work, other implementations can be used.

   **NOTE*: the Datalog implementation above will not work if it is linked to the readline
   library, which happens automatically if you have the readline headers installed. If so, you
   can disable readline support by running the following command after `./configure`:
   
       sed -i "s/-DHAVE_LIBREADLINE=1//g" Makefile # This is terrible

4. [sbt](http://www.scala-sbt.org) version 0.13.9 or higher


In addition, to run the benchmarks and generate graphs, you'll need to install:

 1. The `make` command

 2. [R 3.2.2](https://www.rstudio.com) or higher

 3. [Scala 2.11.7](http://www.scala-lang.org) or higher

    When building and running Rehearsal, SBT downloads its own copy of Scala.
    The benchmarks use Scala in a shell script, so Scala needs to be installed
    independently.

 4. Several R packages: `ggplot2`, `sitools`, `scales`, `plyr`,
    `dplyr`, `grid`, `fontcm`, and `extrafont`.

    You can install these within the R REPL. For example:

    ```
    install.packages("ggplot2")
    ```

    NOTE: R packages require `g++` and `gcc` to be installed.

 5. The *Computer Modern* font for R:

    ```
    R -e "library(extrafont); font_install('fontcm')"
    ```

 6. The `ghostscript` package on Linux systems. (Unclear what is required
    in lieu of ghostscript on Mac OS X.)

### Building and Testing

From the root directory of the repository:

```
sbt compile
sbt test
```

All tests should pass.

### Benchmarking

After building:

```
cd results && make
```


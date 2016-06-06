import java.nio.file._
import java.util.concurrent.{TimeoutException, TimeUnit}

val root = "parser-tests/good"

object Implicits {

  implicit class RichProcess(process: java.lang.Process) {
    import java.io.{BufferedReader, InputStreamReader}


    def attachLogger(logger: scala.sys.process.ProcessLogger): Unit = {
      def makeReader(src: BufferedReader, dst: String => Unit): Runnable = {
        new Runnable {
          override def run(): Unit = {
            try {
              var line = src.readLine()
              while (line != null) {
                dst(line)
                line = src.readLine()
              }
            }
            finally {
              src.close()
            }
          }
        }
      }

      val stdout = new BufferedReader(
        new InputStreamReader(process.getInputStream))
      val stderr = new BufferedReader(
        new InputStreamReader(process.getErrorStream))

      new Thread(makeReader(stdout, str => logger.out(str)))
      new Thread(makeReader(stderr, str => logger.err(str)))
    }

  }

}

trait Command {
  def args: Seq[String]
}

abstract class Benchmark {
  import scala.sys.process._

  private val output = new collection.mutable.StringBuilder()

  def outputln(line: String): Unit = {
    println(line)
    output ++= line
    output += '\n'
  }

  def getOutput(): String = output.toString

  private val logger = scala.sys.process.ProcessLogger(
    line => outputln(line),
    line => println(s"ERROR: $line"))

  def run(commandVal: Command): Int = {
    val prefix = Seq("java", "-Xmx4G",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=info",
      "-Dorg.slf4j.simpleLogger.logFile=rehearsal.log",
      "-jar", "target/scala-2.11/rehearsal.jar")
    (prefix ++ commandVal.args).run(logger).exitValue()
  }
}

def doSizes(output: String): Unit = {
  val sizes = new Benchmark {

    class MyCommand(label: String, filename: String, os: String) extends Command {
      val args = Seq(
        "benchmark-pruning-size",
        "--filename",
        filename,
        "--label",
        label,
        "--os",
        os)
    }

    def bench(label: String, filename: String, os: String = "ubuntu-trusty") = {
      assert(run(new MyCommand(label, filename, os)) == 0)
    }

    outputln("Name, Before, After")
    bench("irc", s"$root/nfisher-SpikyIRC.pp", os = "centos-6")
    bench("monit", s"$root/dhoppe-monit.pp")
    bench("bind", s"$root/thias-bind.pp")
    bench("hosting", s"$root/puppet-hosting_deter.pp")
    bench("dns", s"$root/antonlindstrom-powerdns.pp")
    bench("xinetd", s"$root/ghoneycutt-xinetd.pp")
    bench("jpa", s"$root/pdurbin-java-jpa-tutorial.pp", os = "centos-6")
    bench("ntp", s"$root/thias-ntp.pp")
    bench("rsyslog", s"$root/xdrum-rsyslog.pp")
    bench("nginx", s"$root/BenoitCattie-puppet-nginx.pp")
    bench("amavis", s"$root/mjhas-amavis.pp")
    bench("clamav", s"$root/mjhas-clamav.pp")
    bench("logstash", s"$root/Nelmo-logstash.pp")
  }

  Files.write(Paths.get(output), sizes.getOutput.getBytes)
}

def doDeterminism(trials: Int, output: String): Unit = {
  val determinism = new Benchmark {

    class MyCommand(val label: String, filename: String, os: String,
                  val pruning: Boolean,
                  val commutativity: Boolean,
                  deterministic: Boolean) extends Command {
      val args = Seq(
        "benchmark-determinism",
        "--filename",
        filename,
        "--label",
        label,
        "--os",
        os,
        "--pruning",
        pruning.toString,
        "--commutativity",
        commutativity.toString,
        "--deterministic",
        deterministic.toString,
        "--timeout",
        600.toString)
    }

    def mayTimeout(command: MyCommand): Unit = {
     run(command) match {
       case 0 => {
         for (i <- 0.until(trials - 1)) {
           assert(run(command) == 0)
         }
       }
       case 1 =>
         outputln(s"${command.label},${command.pruning},${command.commutativity},memout")
       case 2 =>
         outputln(s"${command.label},${command.pruning},${command.commutativity},timeout")
     }
    }

    def bench(label: String, filename: String, deterministic: Boolean,
               os: String = "ubuntu-trusty") = {
      mayTimeout(new MyCommand(label, filename, os, false, false, deterministic))
      mayTimeout(new MyCommand(label, filename, os, false, true, deterministic))
      mayTimeout(new MyCommand(label, filename, os, true, true, deterministic))
    }

    outputln("Name, Pruning, Commutativity, Time")
    bench("irc-nondet", s"$root/nfisher-SpikyIRC.pp", false,  os = "centos-6")
    bench("monit", s"$root/dhoppe-monit.pp", true)
    bench("bind", s"$root/thias-bind.pp", true)
    bench("hosting", s"$root/puppet-hosting_deter.pp", true)
    bench("dns-nondet", s"$root/antonlindstrom-powerdns.pp", false)
    bench("xinetd-nondet", s"$root/ghoneycutt-xinetd.pp", false)
    bench("jpa", s"$root/pdurbin-java-jpa-tutorial.pp", true, os = "centos-6")
    bench("ntp-nondet", s"$root/thias-ntp.pp", false)
    bench("rsyslog-nondet", s"$root/xdrum-rsyslog.pp", false)
    bench("nginx", s"$root/BenoitCattie-puppet-nginx.pp", true)
    bench("amavis", s"$root/mjhas-amavis.pp", true)
    bench("clamav", s"$root/mjhas-clamav.pp", true)
    bench("logstash-nondet", s"$root/Nelmo-logstash.pp", false)
  }

  Files.write(Paths.get(output), determinism.getOutput.getBytes)
}

def doIdempotence(trials: Int, output: String): Unit = {
  val idempotence = new Benchmark {

    class MyCommand(label: String, filename: String, os: String,
                  idempotent: Boolean) extends Command {
      val args =
        Seq("benchmark-idempotence", "--filename", filename, "--label", label,
          "--os", os, "--idempotent", idempotent.toString)

    }

    def bench(label: String, filename: String, idempotent: Boolean,
               os: String = "ubuntu-trusty") = {
      assert(run(new MyCommand(label, filename, os, idempotent)) == 0)
    }

    outputln("Name, Time")
    for (i <- 0.until(trials)) {
      bench("monit", s"$root/dhoppe-monit.pp", true)
      bench("bind", s"$root/thias-bind.pp", true)
      bench("hosting", s"$root/puppet-hosting_deter.pp", true)
      bench("dns", s"$root/antonlindstrom-powerdns_deter.pp", true)
      bench("irc", s"$root/spiky-reduced-deterministic.pp", true, os = "centos-6")
      bench("xinetd", s"$root/ghoneycutt-xinetd_deter.pp", true)
      bench("jpa", s"$root/pdurbin-java-jpa-tutorial.pp", true, os = "centos-6")
      bench("ntp", s"$root/thias-ntp_deter.pp", true)
      bench("rsyslog", s"$root/xdrum-rsyslog_deter.pp", true)
      bench("nginx", s"$root/BenoitCattie-puppet-nginx.pp", true)
      bench("amavis", s"$root/mjhas-amavis.pp", true)
      bench("clamav", s"$root/mjhas-clamav.pp", true)
      bench("logstash", s"$root/Nelmo-logstash_deter.pp", true)
    }
  }

  Files.write(Paths.get(output), idempotence.getOutput.getBytes)
}

def doScalability(trials: Int, output: String): Unit = {
  val benchmark = new Benchmark {

    class MyCommand(size: Int) extends Command {
      val args = Seq("scalability-benchmark", "--size", size.toString)
    }

    def bench(size: Int) = {
      assert(run(new MyCommand(size)) == 0)
    }

    outputln("Size, Time")
    for (i <- 0.until(trials)) {
      for (j <- 1.to(7)) {
        bench(j)
      }
    }
  }

  Files.write(Paths.get(output), benchmark.getOutput().getBytes)
}

args match {
  case Array("sizes", output) => doSizes(output)
  case Array("determinism", n, output) => doDeterminism(n.toInt, output)
  case Array("idempotence", n, output) => doIdempotence(n.toInt, output)
  case Array("scalability", n, output) => doScalability(n.toInt, output)
}


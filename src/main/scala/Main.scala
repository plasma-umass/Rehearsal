package rehearsal

import scopt.OptionDef

object Main extends App {

  import edu.umass.cs.extras.Implicits._
  import scala.concurrent._
  import scala.concurrent.duration._
  import ExecutionContext.Implicits.global
  import java.nio.file.Files
  import Implicits._
  import scala.util.{Try, Success, Failure}

  case class Config(
    command: Config => Unit,
    string: Map[String, String],
    bool: Map[String, Boolean],
    int: Map[String, Int])

  def usage(config: Config): Unit = {
    println(parser.usage)
    System.exit(1)
  }

  def pruningSizeBenchmark(config: Config): Unit = {
    val filename = config.string("filename")
    val label = config.string("label")
    val os = config.string("os")
    val g1 = PuppetParser.parseFile(filename.toString)
      .eval.resourceGraph.fsGraph(os)
    val g2 = g1.pruneWrites()
    val paths = g1.expr.fileSets.writes.size
    val postPruningPaths =  g2.pruneWrites.expr.fileSets.writes.size
    println(s"$label, $paths, $postPruningPaths")
  }

  def idempotenceBenchmark(config: Config): Unit = {
    val filename = config.string("filename").toPath
    val label = config.string("label").toPath
    val os = config.string("os")
    val expected = config.bool("idempotent")
    val g = PuppetParser.parseFile(filename.toString).eval.resourceGraph
      .fsGraph(os)
      .expr
    val (isIdempotent, t) = time(g.isIdempotent)
    assert (expected == isIdempotent)
    println(s"$label, $t")
  }

  def determinismBenchmark(config: Config): Unit = {
    val label = config.string("label")
    val pruning = config.bool("pruning")
    val commutativity = config.bool("commutativity")
    val shouldBeDeterministic = config.bool("deterministic")

    val g_ = PuppetParser.parseFile(config.string("filename"))
      .eval.resourceGraph.fsGraph(config.string("os"))

    val (isDeterministic, t) = try {
        Await.result(Future(time {
          val g = if (config.bool("pruning")) g_.pruneWrites() else g_
          g.toExecTree(commutativity).isDeterministic()
        }),
          config.int("timeout").seconds)
    }
    catch {
      case exn:TimeoutException => sys.exit(2)
    }
    assert (shouldBeDeterministic == isDeterministic)
    println(s"$label,$pruning,$commutativity,$t")

  }

  def scalabilityBenchmark(config: Config): Unit = {
    val n = config.int("size")
    import ResourceModel.{File, CInline}
    import scalax.collection.Graph
    import scalax.collection.GraphEdge.DiEdge
    val e = ResourceModel.File(path = "/a".toPath, content = CInline("content"), force = false)
      .compile("ubuntu-trusty")
    val nodes = 0.until(n).map(n => FSGraph.key())
    val graph = FSGraph(nodes.map(n => n -> e).toMap, Graph(nodes: _*))
    val (_, t) = time(graph.pruneWrites().toExecTree().isDeterministic)
    println(s"$n, $t")
  }

  def checker(config: Config): Unit = {
    val os = config.string("os")
    val filename = config.string("filename").toPath
    val predicate = Try(config.string("predicate")) match {
      case Success(s) => if(s.trim.size > 0) Some(s) else None
      case Failure(_) => None
    }

    if (!Set("ubuntu-trusty", "centos-6").contains(os)) {
      println("""Error: supported operating systems are 'ubuntu-trusty' and 'centos-6'""")
      return
    }

    if (!Files.isRegularFile(filename) || !Files.isReadable(filename)) {
      println(s"Error: not a readable file: $filename")
      return
    }

    Try(PuppetParser.parseFile(filename.toString)
      .eval.resourceGraph.fsGraph(os)) match {
      case Success(g) => {
        print("Checking if manifest is deterministic ... ")
        g.pruneWrites().toExecTree().isDeterDiagnostic() match {
          case None => {
            println("no errors found.")
            val deterExpr = g.expr()
            print("Checking if manifest is idempotent ... ")
            if (deterExpr.isIdempotent) {
              println("OK.")
            }
            else {
              println("FAILED.")
              return
            }
            if(predicate.isDefined){
              Try(PredParser.parse(predicate.get)) match {
                case Success(p) => {
                  print("Checking if the given predicate holds ... ")
                  if(deterExpr.isPredicateTrue(p)) println("OK.")
                  else println("FAILED.")
                }
                case Failure(msg) => println(s"Error parsing the predicate: $msg")
              }


            }

          }
          case Some(edges) => {
            println("FAILED.")
            println("These dependencies may be missing:")
            for ((src, dst) <- edges) {
              println(s"$src -> $dst")
            }
          }
        }
      }
      case Failure(ParseError(msg)) => {
        println("Error parsing file.")
        println(msg)
      }
      case Failure(EvalError(msg)) => {
        println("Error evaluating file.")
        println(msg)
      }
      case Failure(PackageNotFound(distro, pkg)) => {
        println(s"Package not found: $pkg.")
      }
      case Failure(exn) => throw exn
    }
  }

  val parser = new scopt.OptionParser[Config]("rehearsal") {

    def string(name: String): OptionDef[String, Config] = {
      opt[String](name).required
        .action((x, c)  => c.copy(string = c.string + (name -> x)))
    }

    def optString(name: String) = {
      opt[String](name).unbounded.optional
        .action((x, c)  => c.copy(string = c.string + (name -> x)))
    }

    def bool(name: String) = {
      opt[Boolean](name).required
        .action((x, c)  => c.copy(bool = c.bool + (name -> x)))
    }

    def int(name: String) = {
      opt[Int](name).required
        .action((x, c)  => c.copy(int = c.int + (name -> x)))
    }

    head("rehearsal", "0.1")

    cmd("check")
        .action((_, c) => c.copy(command = checker))
        .text("Check a Puppet manifest for errors.")
        .children(string("filename"), string("os"), optString("predicate"))

    cmd("benchmark-pruning-size")
      .action((_, c) => c.copy(command = pruningSizeBenchmark))
      .text("Benchmark the effect of pruning on the number of writable paths")
      .children(string("filename"), string("label"), string("os"))

    cmd("benchmark-idempotence")
      .action((_, c) => c.copy(command = idempotenceBenchmark))
      .text("Benchmark the performance of idempotence-checking")
      .children(string("filename"), string("label"), string("os"), bool("idempotent"))

    cmd("benchmark-determinism")
      .action((_, c) => c.copy(command = determinismBenchmark))
      .text("Benchmark determinism checking")
      .children(string("filename"), string("label"), string("os"),
        bool("pruning"), bool("commutativity"), bool("deterministic"), int("timeout"))

    cmd("scalability-benchmark")
      .action((_, c) => c.copy(command = scalabilityBenchmark))
      .text("Benchmark determinism-checking scaling")
      .children(int("size"))
  }

  parser.parse(args, Config(usage, Map(), Map(), Map())) match {
    case None => {
      println(parser.usage)
      System.exit(1)
    }
    case Some(config) => {
      config.command(config)
    }
  }

}

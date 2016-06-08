import rehearsal.FSSyntax.Expr
import rehearsal.{Implicits, PuppetParser}
import edu.umass.cs.extras.Implicits._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import java.nio.file.Files
import scala.util.{Failure, Success, Try}
import rehearsal.PredParser._

/**
  * Created by user on 6/9/16.
  */
class PredicateTests extends FunSuitePlus {

  def getDeterministicExprFromString(str: String): Expr = {
    Try(PuppetParser.parse(str).eval.
      resourceGraph.fsGraph("ubuntu-trusty")) match {
      case Success(g) => g.expr
      case Failure(m) => throw new Exception(m)
    }
  }

  def getDeterministicExprFromFile(filename: String): Expr = {
    Try(PuppetParser.parseFile(filename).eval.
      resourceGraph.fsGraph("ubuntu-trusty")) match {
      case Success(g) => g.expr
      case Failure(m) => throw new Exception(m)
    }
  }


  val root = "Private/parser-tests/good"

  test("Test on empty manifest (negative test)") {
    val e = getDeterministicExprFromString(
      """""")

    val pred = parse("""file?("/myfile")""")
    assert(!e.isPredicateTrue(pred))
  }

  test("Simple Test on simple manifest (negative test)") {
    val e = getDeterministicExprFromString(
      """""")

    val pred = parse("""dir?("/myfile")""")
    assert(!e.isPredicateTrue(pred))
  }

  test("File Contains on simple manifest (1) (negative test)") {
    val e = getDeterministicExprFromString(
      """""")

    val pred = parse("""fileContains?("/myfile","rachit")""")
    assert(!e.isPredicateTrue(pred))
  }

  test("File Contains on simple manifest (2) (negative test)") {
    val e = getDeterministicExprFromString(
      """""")

    val pred = parse("""fileContains?("/user","rachit")""")
    assert(!e.isPredicateTrue(pred))
  }

  test("Simple pred on complex manifest (negative test)") {
    val e = getDeterministicExprFromFile(s"$root/ghoneycutt-xinetd_deter.pp")
    val pred = parse("""dir?("/etc/xinetd.conf")""")
    assert(!e.isPredicateTrue(pred))
  }


  test("Simple pred on simple manifest") {
    val e = getDeterministicExprFromString(
      """
        |file{'/myfile':
        |  content => "hello"
        |}
      """.stripMargin)

    val pred = parse("""file?("/myfile")""")
    assert(e.isPredicateTrue(pred))
  }

  test("Conjunction pred on simple manifest") {
    val e = getDeterministicExprFromString(
      """
        |file{'/myfile':
        |  content => "hello"
        |}
      """.stripMargin)

    val pred = parse("""file?("/myfile") && dir?("/") """)
    assert(e.isPredicateTrue(pred))
  }

  test("File contains test on simple manifest") {
    val e = getDeterministicExprFromString(
      """
        |file{'/myfile':
        |  content => "hello"
        |}
      """.stripMargin)

    val pred = parse("""fileContains?("/myfile","hello")""")
    assert(e.isPredicateTrue(pred))
  }

  test("Existance test on simple manifest") {
    val e = getDeterministicExprFromString(
      """
        |file{'/myfile':
        |  content => "hello"
        |}
      """.stripMargin)

    val pred = parse("""exists?("/myfile") && exists?("/") """)
    assert(e.isPredicateTrue(pred))
  }

  test("Simple pred on complex manifest") {
    val e = getDeterministicExprFromFile(s"$root/ghoneycutt-xinetd_deter.pp")
    val pred = parse("""file?("/etc/xinetd.conf")""")
    assert(e.isPredicateTrue(pred))
  }
}

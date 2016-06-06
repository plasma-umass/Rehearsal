package rehearsal

object Datalog {

  import edu.umass.cs.extras.Implicits._
  import rehearsal.Implicits._

  trait DatalogImpl {
    def eval(assertions: List[Assertion]): List[Fact]
  }

  object RacketDatalog extends DatalogImpl {
    def eval(assertions: List[Assertion]): List[Fact] = {
      import java.nio.file.{Paths, Files}
      import sys.process._
      val db = Files.createTempFile("datalog", ".rkt")
      val dbString = "#lang datalog\n" + assertions.mkString("\n")
      Files.write(db, dbString.getBytes())
      val facts = Seq("racket", db.toString).lineStream.mkString("\n")
      Files.deleteIfExists(db)
      Parser.parseAll(Parser.facts, facts) match {
        case Parser.Success(facts, _) => facts
        case Parser.NoSuccess(msg, _) => throw Unexpected(msg.toString)
      }
    }
  }

  /** Starts much faster than {@code RacketDatalog}. Available at
    * https://sourceforge.net/projects/datalog/.
    */
  object MitreDatalog extends DatalogImpl {
    def eval(assertions: List[Assertion]): List[Fact] = {
      val pb = new ProcessBuilder("./datalog")
      pb.redirectInput(ProcessBuilder.Redirect.PIPE)
      pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
      pb.redirectErrorStream(true);
      val proc = pb.start()
      proc.getOutputStream().writeAllLines(assertions.map(_.toString)).close()
      // First line says "Datalog 2.5"
      val results = proc.getInputStream().readAllLines().drop(1)
      val code = proc.waitFor()
      assert(code == 0, s"Exit code $code")

      // Using Datalog 2.6 on a Linux system, we seem to get the prompts to input an assertion as output.
      // It is more surprising that this does not occur with Datalog 2.5 on Mac OS X.
      def stripPrompt(line: String): String = {
        line.dropWhile(ch => ch == '>' || ch == ' ')
      }
      Parser.parseAll(Parser.facts, results.map(stripPrompt).mkString("\n")) match {
        case Parser.Success(facts, _) => facts
        case Parser.NoSuccess(msg, _) => {
          println(results)
          throw Unexpected(msg.toString)
        }
      }
    }
  }

  private val illegalIdentifierChars = List(':', '(', '`', '\'', ')', '=', ':',
    '.',  '~', '?', '\"', '%', ' ')

  private def isValidIdentifier(str: String): Boolean = {
    str.length >= 1 &&
    str(0).isUpper == false &&
    str.forall(ch => illegalIdentifierChars.contains(ch) == false)
  }

  type Pred = String

  sealed trait Term
  case class Lit(name: String) extends Term {
    assert(isValidIdentifier(name))
    override def toString(): String = name
  }
  case class Var(name: String) extends Term {
    assert(name.length >= 1 && name(0).isUpper &&
           name.forall(ch => ch.isLetterOrDigit || ch == '_'))
    override def toString(): String = name
  }

  case class Fact(pred: Pred, terms: List[Term]) {
    assert(isValidIdentifier(pred))

    override def toString(): String = {
      val termsStr = terms.mkString(",")
      s"$pred($termsStr)"
    }
  }

  sealed trait Assertion
  case class GroundFact(fact: Fact) extends Assertion {
    override def toString(): String = fact.toString + "."
  }
  case class Rule(head: Fact, body: List[Fact]) extends Assertion {
    override def toString(): String = {
     head.toString + " :- " + body.mkString(", ") + "."
    }
  }
  case class Query(fact: Fact) extends Assertion {
    override def toString(): String = fact.toString + "?"
  }

  import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

  private object Parser extends RegexParsers with PackratParsers {

    lazy val id: PackratParser[String] =
      """[0-9a-z]([0-9a-zA-Z_])*""".r ^^ identity

    lazy val term: PackratParser[Term] = (
      id ^^ { x => Lit(x) }
      )

    lazy val fact: PackratParser[Fact] = (
      id ~ "(" ~ repsep(term, ",") ~ ")" ~ "." ^^
        { case pred ~ _ ~ terms ~ _ ~ _ => Fact(pred, terms) }
      )

    lazy val facts: PackratParser[List[Fact]] = rep(fact)
  }


  class ToTerm[A](prefix: String) {
    private val map = scala.collection.mutable.Map[A, Datalog.Term]()
    private val invertMap = scala.collection.mutable.Map[Datalog.Term, A]()
    private val i = Stream.from(0).toIterator

    def apply(key: A): Term = {
      map.get(key) match {
        case None => {
          val n = i.next
          val term = Lit(s"$prefix$n")
          map += key -> term
          invertMap += term -> key
          term
        }
        case Some(term) => term
      }
    }

    def alias(newKey: A, oldKey: A): Unit = {
      assert(map.contains(newKey) == false)
      assert(map.contains(oldKey) == true)
      val term = map(oldKey)
      map += newKey -> term
      // No need to add to invertMap
    }

    def invert(term: Datalog.Term): A = invertMap(term)

    override def toString(): String = map.toString()
  }

  class Evaluator() {
    private var assertions = List[Assertion]()

    var haveQuery = false
    def fact(pred: String, terms: List[Term]): Unit = {
      assert(haveQuery == false)
      assertions = GroundFact(Fact(pred, terms)) :: assertions
    }

    def query(fact: Fact): Unit = {
      haveQuery = true
      assertions = Query(fact) :: assertions
    }

    def rule(head: Fact, body: List[Fact]): Unit = {
      assert(haveQuery == false)
      assertions = Rule(head, body) :: assertions
    }

    def eval(): List[Fact] = {
      val impl: DatalogImpl = MitreDatalog
      impl.eval(assertions.reverse)
    }

  }

}

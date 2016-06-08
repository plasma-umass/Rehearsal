/**
  * Created by Rachit Nigam on 6/8/16.
  */

/*
    p ::= / | p/str
    pred ::=  exists?(p)
            | file?(p)
            | dir?(p)
            | emptydir?(p)
            | true
            | false
            | pred && pred
            | pred || pred
            | !pred
            | (pred)
            | fileContains?(p,str)
 */
package rehearsal
import scala.util.parsing.combinator._
import FSSyntax._
import java.nio.file._

private class PredParser extends RegexParsers with PackratParsers {
  type P[T] = PackratParser[T]

  lazy val parseFilePath: P[Path] = "\"" ~> "[^\"]*".r <~ "\"" ^^ { x => Paths.get(x.toString) }
  lazy val parseContent: P[String] = "\"" ~> "[^\"]*".r <~ "\"" ^^ { x => x.toString }

  lazy val parseTrue: P[Pred] = positioned("true" ^^ { _ => PTrue})
  lazy val parseFalse: P[Pred] = positioned("false" ^^ { _ => PFalse})

  lazy val parseExistsHuh: P[Pred] = positioned("exists?" ~ "(" ~> parseFilePath <~ ")" ^^
    { p => PNot(PTestFileState(p, DoesNotExist)) })
  lazy val parseFileHuh: P[Pred] = positioned("file?" ~ "(" ~> parseFilePath <~ ")" ^^ {p => PTestFileState(p,IsFile)})
  lazy val parseDirHuh: P[Pred] = positioned("dir?" ~ "(" ~> parseFilePath <~ ")" ^^ {p => PTestFileState(p,IsDir)})
  lazy val parseEmptyDirHuh: P[Pred] = positioned("emptydir?" ~ "(" ~> parseFilePath <~ ")" ^^ {p => PTestFileState(p,IsEmptyDir)})
  lazy val parseFileContainsHuh: P[Pred] = "fileContains?" ~ "(" ~> parseFilePath ~ "," ~ parseContent <~ ")" ^^
    { case p ~ _ ~ c => PTestFileState(p,fileContains(c))}

  lazy val parseSimplePred: P[Pred] = positioned {
    parseTrue | parseFalse | parseFileHuh | parseDirHuh | parseExistsHuh | parseEmptyDirHuh | parseFileContainsHuh
  }

  lazy val parseComplexPred: P[Pred] = positioned {
    "(" ~> parseComplexPred <~ ")"  | parseAnd | parseOr | parseNot | parseSimplePred
  }

  lazy val parseNot: P[Pred] = positioned("!" ~> parseComplexPred ^^ { pred => PNot(pred) })
  lazy val parseAnd: P[Pred] = positioned(parseComplexPred ~ "&&" ~ parseComplexPred ^^ { case l ~ _ ~ r => PAnd(l,r)})
  lazy val parseOr: P[Pred] = positioned(parseComplexPred ~ "||" ~ parseComplexPred ^^ { case l ~ _ ~ r => POr(l,r)})


  def parse(str: String): Pred = parseAll(parseComplexPred,str) match {
  case Success(s,_) => s
  case m => throw ParseError(s"$m")
  }

}

object PredParser {
  private val parser = new PredParser()

  /**
    * Parse a given string that represents a predicate on a filesystem
    * @return A [[rehearsal.FSSyntax.Pred]] representing the string
    */
  def parse = parser.parse _
}

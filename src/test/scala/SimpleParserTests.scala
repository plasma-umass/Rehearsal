class SimpleParserTests extends org.scalatest.FunSuite {

  import rehearsal._
  import PuppetParser._
  import java.nio.file.{Files, Paths}
  import scala.collection.JavaConversions._

  for (path <- Files.newDirectoryStream(Paths.get("Private/parser-tests/good"))) {

    test(path.toString) {
      parseFile(path.toString)
    }

  }

  for (path <- Files.newDirectoryStream(Paths.get("Private/parser-tests/bad"))) {

    test(path.toString) {
      intercept[ParseError] {
        parseFile(path.toString)
      }
    }

  }

}

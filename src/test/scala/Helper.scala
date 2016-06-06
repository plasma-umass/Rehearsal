
trait FunSuitePlus extends org.scalatest.FunSuite
  with com.typesafe.scalalogging.LazyLogging {

  import org.scalatest.Outcome

  override def withFixture(test: NoArgTest): Outcome = {
    logger.info(s"Starting test case ${test.name}")
    try {
      test()
    }
    finally {
      logger.info(s"Finished test case ${test.name}")
    }
  }

}

object TestImplicits {

  import scalax.collection.GraphEdge.DiEdge
  import scalax.collection.Graph
  import rehearsal._

  implicit class RichExprGraph(g: Graph[FSSyntax.Expr, DiEdge]) {

    def fsGraph(): FSGraph = {
      val alist = g.nodes.map(n => n.value -> FSGraph.key())
      val m = alist.toMap

      FSGraph(alist.map({ case (k,v) => (v, k) }).toMap,
        Graph(m.values.toSeq: _*) ++
          g.edges.toList.map(edge => DiEdge(m(edge._1.value), m(edge._2.value))))
    }

  }

}

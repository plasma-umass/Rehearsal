package rehearsal

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import com.typesafe.scalalogging.LazyLogging
import Implicits._

case class ExecTree(commutingGroup: List[(FSGraph.Key, FSSyntax.Expr)],
                    branches: List[ExecTree],
                    graph: FSGraph) {

  import FSSyntax._

  val fringeSize: Int = if (branches.isEmpty) 1 else branches.map(_.fringeSize).sum

  def exprs(): Expr =  ESeq(commutingGroup.map(_._2): _*) >> ESeq(branches.map(_.exprs()): _*)

  def size(): Int = 1 + branches.map(_.size).sum

  def isDeterministic(): Boolean = {
    val e = this.exprs()
    val sets = e.fileSets
    val ro = sets.reads
    val symb = new SymbolicEvaluatorImpl(e.paths.toList, e.hashes, ro)
    try {
      symb.isDeter(this)
    }
    finally {
      symb.free()
    }
  }

  def isDeterDiagnostic()  = {
    val e = this.exprs()
    val sets = e.fileSets
    val ro = sets.reads
    val symb = new SymbolicEvaluatorImpl(e.paths.toList, e.hashes, ro)
    try {
      symb.isDeterDiagnostic(this)
    }
    finally {
      symb.free()
    }
  }

  def isDeterError(): Boolean = {
    val e = this.exprs()
    val sets = e.fileSets
    val ro = sets.reads
    val symb = new SymbolicEvaluatorImpl(e.paths.toList, e.hashes, ro)
    try {
      symb.isDeterError(this)
    }
    finally {
      symb.free()
    }
  }

}

object FSGraph {

  trait Key
  case class Unlabelled private[FSGraph](index: Int) extends Key

  private var i = 0

  def key(): Key = {
    val n = i
    i = i + 1
    Unlabelled(n)
  }

}

// A potential issue with graphs of FS programs is that several resources may
// compile to the same FS expression. Slicing makes this problem more likely.
// To avoid this problem, we keep a map from unique keys to expressions and
// build a graph of the keys. The actual values of the keys don't matter, so
// long as they're unique. PuppetSyntax.Node is unique for every resource, so
// we use that when we load a Puppet file. For testing, the keys can be
// anything.
case class FSGraph(exprs: Map[FSGraph.Key, FSSyntax.Expr], deps: Graph[FSGraph.Key, DiEdge])
  extends LazyLogging {

  import FSGraph._

  lazy val size: Int = {
    deps.nodes.map(n => exprs(n).size).reduce(_ + _) + deps.edges.size
  }

  /** Returns an FS program that represents the action of a <b>deterministic</b> graph.
    *
    * @return an FS program
    */
  def expr(): FSSyntax.Expr = {
    FSSyntax.ESeq(deps.topologicalSort().map(k => exprs(k)): _*)
  }


  def toExecTree(commuteOpt: Boolean = true): ExecTree = {

    def loop(g: Graph[Key, DiEdge]): List[ExecTree] = {
      def commutesWithRest(key: Key): Boolean = {
        val node = g.get(key)
        val others = g.nodes.toSet -- g.descendants(node) - node
        others.forall(node_ => exprs(node_.value).commutesWith(exprs(key)))
      }

      if (g.isEmpty) {
        Nil
      }
      else {
        val fringe = g.nodes.filter(_.inDegree == 0).toList.map(_.value)


        val fixed = if (commuteOpt == false) Nil else fringe.filter(commutesWithRest)

        if (fixed.isEmpty || fixed.length != fringe.length) {
          fringe.map(key => ExecTree(List((key, exprs(key))), loop(g - key), this))
        }
        else {
          List(ExecTree(fixed.map(k => (k, exprs(k))), loop(g -- fixed), this))
        }
      }
    }

    logTime("building execution tree") {
      loop(deps) match {
        case List(node) => node
        case alist => ExecTree(Nil, alist, this)
      }
    }
  }

  /** Prunes writes from this graph to make determinism-checking faster. */
  def pruneWrites(): FSGraph = {
    val n = allPaths.size
    val r = logTime("Pruning writes") {
      val nodes = this.deps.topologicalSort.reverse
      nodes.foldLeft(this) {
        case (FSGraph(exprs, graph), nodeValue) => {
          val node = graph.get(nodeValue)
          val succs = graph.nodes.toSet -- graph.ancestors(node) - node
          val commutesAll = succs.forall { succ =>
            this.exprs(node.value).commutesWith(this.exprs(succ.value))
          }
          if (commutesAll) FSGraph(exprs - node, graph.shrink(node))
          else FSGraph(exprs, graph)
        }
      }
    }
    val m = r.allPaths.size
    logger.info(s"Pruning removed ${n - m} paths")
    DeterminismPruning.pruneWrites(r)
  }

  /** All paths used by the nodes of this graph. */
  lazy val allPaths = this.deps.nodes.map(n => this.exprs(n).paths)
    .foldLeft(Set.empty[Path])(_ union _)

}

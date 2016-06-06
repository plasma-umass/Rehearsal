package rehearsal

object DeterminismPruning extends com.typesafe.scalalogging.LazyLogging   {

  import edu.umass.cs.extras.Implicits._
  import FSSyntax._
  import Implicits._

  sealed trait AStat {
    def ==(other: FileState): Boolean = (this, other) match {
      case (ADir, IsDir) => true
      case (AFile, IsFile) => true
      case (ADoesNotExist, DoesNotExist) => true
      case _ => false
    }
  }
  case object ADir extends AStat
  case object AFile extends AStat
  case object ADoesNotExist extends AStat
  case object AUntouched extends AStat

  def specPred(pred: Pred, h: Map[Path, AStat]): Pred = pred match {
    case PTestFileState(p, s) => {
      if (!h.contains(p)) {
        pred
      }
      else {
        if (h(p) == s) PTrue else PFalse
      }
    }
    case PTrue | PFalse => pred
    case POr(e1, e2) => specPred(e1, h) || specPred(e2, h)
    case PAnd(e1, e2) => specPred(e1, h) && specPred(e2, h)
    case PNot(e) => !specPred(e, h)
  }

  def pruneRec(canPrune: Set[Path], e: Expr, h: Map[Path, AStat]):
  (Expr, Map[Path, AStat]) = {
    e match {
      case ESkip => (e, h)
      case EError => (e, h)
      case EMkdir(p) if canPrune.contains(p) =>
        (ite(testFileState(p.getParent, IsDir),
          ESkip, EError),
          h + (p -> ADir) + (p.getParent -> ADir))
      case EMkdir(p) => (e, h + (p -> ADir) + (p.getParent -> ADir))
      case ECreateFile(p, _) if canPrune.contains(p) =>
        (ite(testFileState(p.getParent, IsDir),
          ESkip, EError),
          h + (p -> AFile) + (p.getParent -> ADir))
      case ECreateFile(p, _) => (e, h + (p -> AFile) + (p.getParent -> ADir))
      /*TODO [Rian]: this only prunes RmFiles not RmDirs; need IsEmpty predicate*/
      case ERm(p) if canPrune.contains(p) => (ESkip,
        h + (p -> ADoesNotExist))
      case ERm(_) => (e, h)
      case ECp(p1, p2) if canPrune.contains(p2) =>
        (ite(testFileState(p1, IsFile) &&
          testFileState(p2.getParent, IsDir),
          ESkip, EError),
          h + (p1 -> AFile) + (p2 -> AFile) + (p2.getParent -> ADir))
      case ECp(p1, p2) => (e, h + (p1 -> AFile) + (p2 -> AFile) + (p2.getParent -> ADir))
      case ESeq(e1, e2) => {
        val e1Pruned = pruneRec(canPrune, e1, h)
        val e2Pruned = pruneRec(canPrune, e2, h ++ e1Pruned._2)
        (e1Pruned._1 >> e2Pruned._1, e2Pruned._2)
      }
      case EIf(a, e1, e2) => {
        val e1Pruned = pruneRec(canPrune, e1, h)
        val e2Pruned = pruneRec(canPrune, e2, h)
        (ite(specPred(a, h), e1Pruned._1, e2Pruned._1), h)
      }
    }
  }

  def prune(canPrune: Set[Path], e: Expr): Expr = {
    val result = pruneRec(canPrune, e, Map())._1
    result
  }

  sealed trait TrivialStatus
  case object OnlyFile extends TrivialStatus
  case object OnlyDirectory extends TrivialStatus
  case object Unknown extends TrivialStatus

  def join2(branching: Set[Path], s1: Map[Path,(Set[Path], TrivialStatus)],
            s2: Map[Path,(Set[Path], TrivialStatus)]): Map[Path,(Set[Path], TrivialStatus)] = {
    s1.combine(s2)(
      { case ((set1, x), (set2, y)) => if (x == y) (set1 union set2, x) else (Set(), Unknown) },
      { case (set, x) => (branching union set, x) },
      { case (set, x) => (branching union set, x) })
  }

  def trivialStatus2(expr: Expr): Map[Path, (Set[Path], TrivialStatus)] = expr match {
    case EIf(pred, e1, e2) => join2(pred.readSet, trivialStatus2(e1), trivialStatus2(e2))
    case ESeq(e1, e2) => join2(Set(), trivialStatus2(e1), trivialStatus2(e2))
    case ECreateFile(p, _) => Map(p -> (Set(p), OnlyFile))
    case EMkdir(p) => Map(p -> (Set(p), OnlyDirectory))
    case ECp(_, p) => Map(p -> (Set(p), OnlyFile))
    case ERm(_) => Map()
    case ESkip => Map()
    case EError => Map()
  }

  def definitiveWrites(exclude: Set[Path], expr: Expr): scala.Seq[Path] = {
    trivialStatus2(expr).toSeq.filter({ case (_, (reads, status)) => status != Unknown && reads.intersect(exclude).isEmpty } ).map(_._1)
  }

  def candidates(exprs: Map[FSGraph.Key, Expr]): Map[FSGraph.Key, Set[Path]] = {
    // Maps each path to the number of resources that contain it
    val counts = exprs.values.toSeq.flatMap(_.paths.toSeq)
      .groupBy(identity).mapValues(_.length)
    // All the paths that exist in more than one resource.
    val exclude = counts.filter({ case (_, n) => n > 1 }).keySet
    exprs.mapValues(e => e.paths diff exclude)
  }

  def pruningCandidates2(exprs: Map[FSGraph.Key, Expr]): Map[FSGraph.Key, Set[Path]] = {
    val counts = exprs.values.toSeq.flatMap(_.paths.toSeq)
      .groupBy(identity).mapValues(_.length)
    // All the paths that exist in more than one resource.
    val exclude = counts.filter({ case (_, n) => n > 1 }).keySet

    val candidateMap = candidates(exprs)
    val gen = for ((res, expr) <- exprs) yield {
      val definitive = definitiveWrites(exclude, expr)
      val noninterfering = definitive.toSet intersect candidateMap(res)
      (res, noninterfering)
    }
    gen.toMap
  }

  def pruneWrites(graph: FSGraph): FSGraph = {
    val candidates = pruningCandidates2(graph.exprs)
    val exprs_ = graph.exprs.map {
      case (k, e) => (k, pruneRec(candidates(k), e, Map())._1)
    }.toMap

    graph.copy(exprs = exprs_)
  }

}

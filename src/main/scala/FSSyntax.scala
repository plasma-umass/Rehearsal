package rehearsal

import scala.util.parsing.input.Positional

object FSSyntax {

  sealed trait FileState extends Ordered[FileState] {

    def compare(that: FileState): Int = {
      def toInt(x: FileState): Int = x match {
        case IsFile => 0
        case IsDir => 1
        case DoesNotExist => 2
        case IsEmptyDir => 3
        case fileContains(c) => 4 + c.hashCode
      }
      val x = toInt(this).compare(toInt(that))
      if (x > 0) 1 else if (x < 0) -1 else 0
    }

  }

  case object IsFile extends FileState
  case object IsDir extends FileState
  case object IsEmptyDir extends FileState
  case object DoesNotExist extends FileState
  case class fileContains(content: String) extends FileState

  sealed trait Pred extends Positional{
    def size = Helpers.predSize(this)

    lazy val readSet = Commutativity.predReadSet(this)


    def hashes: Set[String] = this match {
      case PTrue | PFalse => Set()
      case PAnd(a,b)  => a.hashes union b.hashes
      case POr(a,b) => a.hashes union b.hashes
      case PNot(p) => p.hashes
      case PTestFileState(_, fileContains(c)) => Set(c)
      case PTestFileState(_,_) => Set()
    }

    def &&(b: Pred): Pred = (this, b) match {
      case (PTrue, _) => b
      case (_, PTrue) => this
      case _ => internPred(PAnd(this, b))
    }

    def ||(b: Pred): Pred = (this, b) match {
      case (PTrue, _) => PTrue
      case (PFalse, _) => b
      case (_, PTrue) => PTrue
      case (_, PFalse) => this
      case _ => internPred(POr(this, b))
    }

    def unary_!(): Pred = this match {
      case PNot(PTrue) => PFalse
      case PNot(PFalse) => PTrue
      case PNot(a) => a
      case _ => internPred(PNot(this))
    }

  }

  case object PTrue extends Pred
  case object PFalse extends Pred
  case class PAnd private[FSSyntax](a: Pred, b: Pred) extends Pred
  case class POr private[FSSyntax](a: Pred, b: Pred) extends Pred
  case class PNot private[FSSyntax](a: Pred) extends Pred
  case class PTestFileState private[FSSyntax](path: Path, s: FileState) extends Pred {
    def <(tfs: PTestFileState): Boolean = (this, tfs) match {
      case (PTestFileState(f, x), PTestFileState(g, y)) => if (f.toString == g.toString) {
        x < y
      } else {
        f.toString < g.toString
      }
    }
  }

  sealed abstract trait Expr {
    def pretty(): String = Pretty.pretty(this)

    def commutesWith(other: Expr) = commutativityCache.get((this, other)) match {
      case Some(r) => r
      case None => {
        val e1 = this
        val e2 = other
        val r = e1.fileSets.commutesWith(e2.fileSets) || ((e1 >> e2).equivalentTo(e2 >> e1))
        commutativityCache += (this, other) -> r
        r
      }
    }

    lazy val size = Helpers.size(this)
    lazy val paths = Helpers.exprPaths(this)
    lazy val hashes = Helpers.exprHashes(this)

    lazy val fileSets = Commutativity.absState(this)

    override def toString(): String = this.pretty()

    def >>(e2: Expr) = (this, e2) match {
      case (ESkip, _) => e2
      case (_, ESkip) => this
      case (EError, _) => EError
      case (_, EError) => EError
      case (EIf (a, ESkip, EError), EIf (b, ESkip, EError)) => ite(a && b, ESkip, EError)
      case _ => seq(this, e2)
    }

    def eval(st: FSEvaluator.State): Option[FSEvaluator.State] = FSEvaluator.eval(st, this)

    def isIdempotent(): Boolean = {
      val impl = new SymbolicEvaluatorImpl(this.paths.toList, this.hashes, Set())
      val r = impl.isIdempotent(this)
      impl.smt.free()
      r
    }

    def isPredicateTrue(pred: Pred): Boolean = {
      import PredParser._
      val impl = new SymbolicEvaluatorImpl(this.paths.toList union pred.readSet.toList,
        this.hashes union pred.hashes, Set())
      val r = impl.isPredicateTrue(pred,this)
      impl.smt.free()
      r
    }

    /** Flattens a nested sequence. */
    def flatten(): Seq[Expr] = this match {
      case ESeq(head, tail) => head.flatten() ++ tail.flatten()
      case other => Seq(other)
    }

    /** Returns {@code true} if both expressions are equivalent. */
    def equivalentTo(other: Expr): Boolean = {
      val allPaths = (this.paths union other.paths).toList
      val emptyDirs = Helpers.testedEmptyDirs(this) union Helpers.testedEmptyDirs(other)
      val addedPaths = (emptyDirs intersect Helpers.fringe(allPaths))
        .map(p => p.resolve("__child__")).toList
      val impl = new SymbolicEvaluatorImpl(
        addedPaths ++ allPaths,
        other.hashes union other.hashes,
        Set())
      val result = impl.exprEquals(this, other)
      impl.free()
      result.isEmpty
    }

  }

  case object EError extends Expr
  case object ESkip extends Expr
  case class EIf private[FSSyntax](a: Pred, p: Expr, q: Expr) extends Expr
  case class ESeq private[FSSyntax](p: Expr, q: Expr) extends Expr
  case class EMkdir private[FSSyntax](path: Path) extends Expr
  case class ECreateFile private[FSSyntax](path: Path, contents: String) extends Expr
  case class ERm private[FSSyntax](path: Path) extends Expr
  case class ECp private[FSSyntax](src: Path, dst: Path) extends Expr

  private val exprCache = scala.collection.mutable.HashMap[Expr, Expr]()
  private val predCache = scala.collection.mutable.HashMap[Pred, Pred]()
  private val commutativityCache = scala.collection.mutable.HashMap[(Expr, Expr), Boolean]()

  private def internPred(a: Pred) = predCache.getOrElseUpdate(a, a)

  private def intern(e: Expr): Expr = exprCache.getOrElseUpdate(e, e)

  def ite(a: Pred, p: Expr, q: Expr): Expr = {
    if (p eq q) p else intern(new EIf(a, p, q))
  }

  def seq(p: Expr, q: Expr) = intern(ESeq(p, q))

  def mkdir(p: Path) = intern(EMkdir(p))

  def createFile(p: Path, c: String) = intern(ECreateFile(p, c))

  def rm(p: Path) = intern(ERm(p))

  def cp(src: Path, dst: Path) = intern(ECp(src, dst))

  def testFileState(p: Path, s: FileState): PTestFileState = internPred(PTestFileState(p, s)).asInstanceOf[PTestFileState]

  def clearCache(): Unit = {
    commutativityCache.clear()
    exprCache.clear()
    predCache.clear()
  }

  object ESeq {
    import Implicits._
    def apply(es: Expr*): Expr = es.foldRight(ESkip: Expr)((e, expr) => e >> expr)
  }

  object PAnd {
    def apply(preds: Pred*): Pred = preds.foldRight[Pred](PTrue) { (x, y) => PAnd(x, y) }
  }

}

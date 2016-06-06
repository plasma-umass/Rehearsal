package rehearsal

private[rehearsal] object Helpers {
  import Implicits._
  import FSSyntax._

  def predSize(pred: Pred): Int = pred match {
    case PNot(a) => 1 + a.size
    case PAnd(a, b) => 1 + a.size + b.size
    case POr(a, b) => 1 + a.size + b.size
    case PTestFileState(_, _) => 1
    case PTrue => 1
    case PFalse => 1
  }

  def size(expr: Expr): Int = expr match {
    case EError => 1
    case ESkip => 1
    case EIf(a, p, q) => 1 + a.size + p.size + q.size
    case ESeq(p, q) => 1 + p.size + q.size
    case EMkdir(_) => 1
    case ECreateFile(_, _) => 1
    case ERm(_) => 1
    case ECp(_, _) => 1
  }

  def exprPaths(expr: Expr): Set[Path] = expr match {
    case EError => Set()
    case ESkip => Set()
    case EIf(a, p, q) => a.readSet union exprPaths(p) union exprPaths(q)
    case ESeq(p, q) => exprPaths(p) union exprPaths(q)
    case EMkdir(f) => f.ancestors() + f
    case ECreateFile(f, _) => f.ancestors() + f
    case ERm(f) => f.ancestors() + f
    case ECp(src, dst) => src.ancestors() union dst.ancestors() union Set(src, dst)
  }

  def exprHashes(expr: Expr): Set[String] = expr match{
    case EError => Set()
    case ESkip => Set()
    case EIf(a, p, q) => p.hashes union q.hashes
    case ESeq(p, q) => p.hashes union q.hashes
    case EMkdir(f) => Set()
    case ECreateFile(f, h) => Set(h)
    case ERm(f) => Set()
    case ECp(src, dst) => Set()
  }

  /** Returns the set of paths that are tested to see if they are empty. */
  def testedEmptyDirs(e: Expr): Set[Path] = {

    def pred(pr: Pred, set: Set[Path]): Set[Path] = pr match {
      case PNot(a) => pred(a, set)
      case PAnd(a, b) => pred(b, pred(a, set))
      case POr(a, b) => pred(b, pred(a, set))
      case PTestFileState(p, IsEmptyDir) => set + p
      case PTestFileState(_, _) => set
      case PTrue => set
      case PFalse => set
    }

    def expr(e: Expr, set: Set[Path]): Set[Path] = e match {
      case EError => set
      case ESkip => set
      case EIf(a, p, q) => expr(q, expr(p, pred(a, set)))
      case ESeq(p, q) => expr(q, expr(p, set))
      case EMkdir(_) => set
      case ECreateFile(_, _) => set
      case ERm(_) => set
      case ECp(_, _) => set
    }
    expr(e, Set())
  }

  /** Filters the set of path, returning only those paths that have no children
    * in the set.
    *
    * For example, @code{fringe(/a, /a/b, /a/b/c) == Set(/a/b/c)}.
    */
  def fringe(paths: Iterable[Path]): Set[Path] = {
    paths.filter(p1 => !paths.exists(p2 => p2.getParent == p1)).toSet
  }

}

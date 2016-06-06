package rehearsal

object FSEvaluator {

  import FSSyntax._

  type S = Option[State]

  sealed trait FState
  case object FDir extends FState
  case class FFile(hash: String) extends FState
  type State = Map[Path, FState]

  def isDir(st: State, p: Path): Boolean = st.get(p) == Some(FDir)

  def doesNotExist(st: State, p: Path): Boolean = !st.contains(p)

  def isFile(st: State, p: Path): Boolean = st.get(p) match {
    case Some(FFile(_)) => true
    case _ => false
  }

  def isEmptyDir(st: State, p: Path) = {
    !st.toSeq.exists({ case (p1, s) => p1.startsWith(p) && p1 != p })
  }

  def evalPred(st: State, pred: Pred): Boolean = pred match {
    case PTrue => true
    case PFalse => false
    case PAnd(a, b) => evalPred(st, a) && evalPred(st, b)
    case POr(a, b) =>  evalPred(st, a) || evalPred(st, b)
    case PNot(a) => !evalPred(st ,a)
    case PTestFileState(p, IsFile) => isFile(st, p)
    case PTestFileState(p, IsDir) => isDir(st, p)
    case PTestFileState(p, DoesNotExist) => doesNotExist(st, p)
    case PTestFileState(p, IsEmptyDir) => st.keys.exists(_.getParent == p)
  }

  def eval(st: State, expr: Expr): S = expr match {
    case EError => None
    case ESkip => Some(st)
    case EMkdir(p) => {
      if (doesNotExist(st, p) && isDir(st, p.getParent)) {
        Some(st + (p -> FDir))
      }
      else {
        None
      }
    }
    case ECreateFile(p, h) => {
      if (isDir(st, p.getParent) && doesNotExist(st, p)) {
        Some(st + (p -> FFile(h)))
      }
      else {
        None
      }
    }
    case ECp(src, dst) => ???
    case ERm(p) => {
      if (isFile(st, p) || isEmptyDir(st, p)) {
        Some(st - p)
      }
      else {
        None
      }
    }
    case ESeq(e1, e2) => eval(st, e1).flatMap(st_ => eval(st_, e2))
    case EIf(pred, e1, e2) => {
      if (evalPred(st, pred)) {
        eval(st, e1)
      }
      else {
        eval(st, e2)
      }
    }
  }

  def evalErr(s: S, expr: Expr): S = s match {
    case None => None
    case Some(st) => eval(st, expr)
  }

}

package rehearsal

private[rehearsal] object Pretty {

  import FSSyntax._

  sealed abstract trait PredCxt
  case object AndCxt extends PredCxt
  case object OrCxt extends PredCxt
  case object NotCxt extends PredCxt

  def prettyPred(cxt: PredCxt, pred: Pred): String = pred match {
    case PTrue => "true"
    case PFalse => "false"
    case PTestFileState(p, s) => s"$s($p)"
    case PNot(PTrue) => "!true"
    case PNot(PFalse) => "!false"
    case PNot(PTestFileState(p, s)) => s"!$s($p)"
    case PNot(a) => s"!(${prettyPred(NotCxt, a)})"
    case PAnd(p, q) => {
      val pStr = prettyPred(AndCxt, p)
      val qStr = prettyPred(AndCxt, q)
      cxt match {
        case AndCxt | NotCxt => s"$pStr && $qStr"
        case OrCxt => s"($pStr && $qStr)"
      }
    }
    case POr(p, q) => {
      val pStr = prettyPred(OrCxt, p)
      val qStr = prettyPred(OrCxt, q)
      cxt match {
        case AndCxt => s"($pStr || $qStr)"
        case OrCxt | NotCxt => s"$pStr || $qStr"
      }
    }
  }

  def pretty(expr: Expr): String = expr match {
    case EError => "error"
    case ESkip => "skip"
    case EIf(a, p, q) => s"If(${prettyPred(NotCxt, a)}, ${pretty(p)}, ${pretty(q)})"
    case ESeq(p, q) => pretty(p) + " >> " + pretty(q)
    case ECreateFile(path, hash) => s"createFile($path)"
    case ERm(path) => s"rm($path)"
    case EMkdir(path) => s"mkdir($path)"
    case ECp(src, dst) => s"cp($src, $dst)"
  }

}

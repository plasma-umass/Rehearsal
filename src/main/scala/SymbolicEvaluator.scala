package rehearsal

import edu.umass.cs.extras.Implicits._
import FSEvaluator._
import smtlib.theories.Core.{BoolSort, Equals}
import edu.umass.cs.smtlib._
import smtlib._
import parser._
import Commands._
import Terms._

import scala.util.{Failure, Success, Try}
import CommandsResponses._
import java.nio.file.Paths

import FSSyntax._
import rehearsal.Implicits._
import rehearsal.PuppetSyntax.ResourceGraph
import smtlib.interpreters.Z3Interpreter

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge

object SymbolicEvaluator {

  def predEquals(a: Pred, b: Pred): Boolean = {
    val impl = new SymbolicEvaluatorImpl((a.readSet union b.readSet).toList, Set(), Set())
    val result = impl.predEquals(a, b)
    impl.free()
    result
  }

}

case class ST(isErr: Term, paths: Map[Path, Term])

class SymbolicEvaluatorImpl(allPaths: List[Path],
                            hashes: Set[String],
                            readOnlyPaths: Set[Path]
                            ) extends com.typesafe.scalalogging.LazyLogging {
  import SMT._
  import SMT.Implicits._

  logger.info(s"Started with ${allPaths.size} paths, ${hashes.size} hashes, and ${readOnlyPaths.size} read-only paths")

  val writablePaths = allPaths.filterNot(p => readOnlyPaths.contains(p))

  val childrenOf: Map[Path, List[Path]] = allPaths.map(p => p -> allPaths.filter(_.getParent == p)).toMap

  for (p <- writablePaths) {
    logger.debug(s"$p is writable")
  }
  for (p <- readOnlyPaths) {
    logger.debug(s"$p is read-only")
  }

  val smt = new SMT(new Z3Interpreter(Settings.z3Path, Array("-in", "-smt2")))

  import smt.eval

  eval(DeclareSort(SSymbol("hash"), 0))

  val hashSort = Sort(SimpleIdentifier(SSymbol("hash")))

  val hashToZ3: Map[String, Term] = hashes.toList.map(h => {
    val x = freshName("hash")
    eval(DeclareConst(x, hashSort))
    (h, QualifiedIdentifier(Identifier(x)))
  }).toMap

  if(hashToZ3.size != 0)  {
    val hashes = hashToZ3.values.toSeq
    eval(Assert(FunctionApplication("distinct".id, hashes)))
    val x = freshName("h")
    eval(Assert(Forall(SortedVar(x, hashSort), Seq(),
      hashes.map(h => Equals(x.id, h)).or())))
  }

  val termToHash: Map[Term, String] = hashToZ3.toList.map({ case (x,y) => (y, x) }).toMap

  // type stat = IsDir | DoesNotExist | IsFile of hash
  eval(DeclareDatatypes(Seq((SSymbol("stat"),
    Seq(Constructor(SSymbol("IsDir"), Seq()),
      Constructor(SSymbol("DoesNotExist"), Seq()),
      Constructor(SSymbol("IsFile"), Seq((SSymbol("hash"), hashSort))))))))

  val statSort = Sort(SimpleIdentifier(SSymbol("stat")))

  val readOnlyMap = {
    val ids = readOnlyPaths.map(p => {
      val z = freshName("path")
      ((p, QualifiedIdentifier(Identifier(z))), DeclareConst(z, statSort))
    })
    val (paths, cmds) = ids.unzip
    for (c <- cmds) { eval(c) }
    paths.toMap
  }
  val initState = freshST()

  val reverseMap = initState.paths.map(x => (x._2.asInstanceOf[QualifiedIdentifier].id.symbol.name, x._1))
  val reverseHash = hashToZ3.map(x => (x._2.asInstanceOf[QualifiedIdentifier].id.symbol.name, x._1))

  // Ensures that all paths in st form a proper directory tree. If we assert
  // this for the input state and all operations preserve directory-tree-ness,
  // then there is no need to assert it for all states.
  def assertPathConsistency(st: ST): Unit = {
    val root = Paths.get("/")
    for (p <- allPaths) {
      if (p == root) {
        eval(Assert(FunctionApplication("is-IsDir".id, Seq(st.paths(p)))))
      }
      // If the parent of p is "/", there is no need to assert when "/" may
      // be a directory, due to the assertion above. In addition, if the
      // parent of p is not represented, there is no need for the assertion
      // either.
      else if (p.getParent != root && st.paths.contains(p.getParent)) {
        val pre = FunctionApplication("is-IsFile".id, Seq(st.paths(p))) ||
          FunctionApplication("is-IsDir".id, Seq(st.paths(p)))
        val post = FunctionApplication("is-IsDir".id, Seq(st.paths(p.getParent)))
        eval(Assert(pre ==> post))
      }
    }
  }

  def freshST(): ST = {

    val ids = writablePaths.map(p => {
      val z = freshName("path")
      ((p, QualifiedIdentifier(Identifier(z))), DeclareConst(z, statSort))
    })
    val (paths, cmds) = ids.unzip
    val isErr = freshName("isErr")
    val commands = DeclareConst(isErr, BoolSort()) +: cmds
    commands.map(eval(_))
    ST(QualifiedIdentifier(Identifier(isErr)), paths.toMap ++ readOnlyMap)
  }

  def stEquals(st1: ST, st2: ST): Term = {
    (st1.isErr && st2.isErr) ||
      (!st1.isErr && !st2.isErr && writablePaths.map(p => Equals(st1.paths(p), st2.paths(p))).and())
  }

  def evalPred(st: ST, pred: Pred): Term = pred match {
    case PTrue => true.term
    case PFalse => false.term
    case PNot(a) => !evalPred(st, a)
    case PAnd(a, b) => evalPred(st, a) && evalPred(st, b)
    case POr(a, b) => evalPred(st, a) || evalPred(st, b)
    case PTestFileState(p, IsDir) => Equals(st.paths(p), "IsDir".id)
    case PTestFileState(p, IsEmptyDir) => {
      val children = childrenOf(p).map(p => st.paths(p))
      Equals(st.paths(p), "IsDir".id) &&
      children.map(p => Equals(p,"DoesNotExist".id)).and()
    }
    case PTestFileState(p, DoesNotExist) => Equals(st.paths(p), "DoesNotExist".id)
    case PTestFileState(p, IsFile) =>
      FunctionApplication("is-IsFile".id, Seq(st.paths(p)))
  }

  def predEquals(a: Pred, b: Pred): Boolean = smt.pushPop {
    val st = initState
     eval(Assert(!Equals(evalPred(st, a), evalPred(st, b))))
    !smt.checkSat()
  }

  def ifST(b: Term, st1: ST, st2: ST): ST = {
    ST(ite(b, st1.isErr, st2.isErr),
      writablePaths.map(p => (p, ite(b, st1.paths(p), st2.paths(p)))).toMap ++ readOnlyMap)
  }

  def evalExpr(st: ST, expr: Expr): ST = expr match {
    case ESkip => st
    case EError => ST(true.term, st.paths)
    case ESeq(p, q) => //evalExpr(evalExpr(st, p), q)
    {
      val stInter = evalExpr(st, p)
      val isErr = freshName("isErr")
      eval(DeclareConst(isErr, BoolSort()))
      val stInter1 = stInter.copy(isErr = isErr.id)
      eval(Assert(Equals(stInter.isErr, stInter1.isErr)))
      evalExpr(stInter1, q)
    }
    case EIf(a, e1, e2) => {
      val st1 = evalExpr(st, e1)
      val st2 = evalExpr(st, e2)
      val b = freshName("b")
      eval(DeclareConst(b, BoolSort()))
      eval(Assert(Equals(b.id, evalPred(st, a))))
      val isErr = freshName("isErr")
      eval(DeclareConst(isErr, BoolSort()))
      eval(Assert(Equals(isErr.id, ite(b.id, st1.isErr, st2.isErr))))
      ST(isErr.id,
        writablePaths.map(p => (p, ite(b.id, st1.paths(p), st2.paths(p)))).toMap ++ readOnlyMap)
    }
    case ECreateFile(p, h) => {
      assert(readOnlyPaths.contains(p) == false)
      val pre = Equals(st.paths(p), "DoesNotExist".id) && Equals(st.paths(p.getParent), "IsDir".id)
      ST(st.isErr ||  !pre,
        st.paths + (p -> FunctionApplication("IsFile".id, Seq(hashToZ3(h)))))
    }
    case EMkdir(p) => {
      assert(readOnlyPaths.contains(p) == false,
        s"Mkdir($p) found, but path is read-only")
      val pre = Equals(st.paths(p), "DoesNotExist".id) && Equals(st.paths(p.getParent), "IsDir".id)
      ST(st.isErr || !pre,
        st.paths + (p -> "IsDir".id))
    }
    case ERm(p) => {
      assert(readOnlyPaths.contains(p) == false)
      // TODO(arjun): May not need to check all descendants. It should be enough
      // to check if immediate children do not exist.
      val descendants = st.paths.filter(p1 => p1._1 != p && p1._1.startsWith(p)).map(_._2).toSeq
      val pre = FunctionApplication("is-IsFile".id, Seq(st.paths(p))) ||
        (FunctionApplication("is-IsDir".id, Seq(st.paths(p))) &&
          descendants.map(p_ => Equals(p_, "DoesNotExist".id)).and())
      ST(st.isErr || !pre,
        st.paths + (p -> "DoesNotExist".id))
    }
    case ECp(src, dst) => {
      assert(readOnlyPaths.contains(dst) == false)
      val pre = FunctionApplication("is-IsFile".id, Seq(st.paths(src))) &&
        Equals(st.paths(dst.getParent), "IsDir".id) &&
        Equals(st.paths(dst), "DoesNotExist".id)
      ST(st.isErr || !pre,
        st.paths + (dst -> FunctionApplication("IsFile".id,
          Seq(FunctionApplication("hash".id, Seq(st.paths(src)))))))
    }
  }

  def fstateFromTerm(term: Term): Option[FSEvaluator.FState] = term match {
    case QualifiedIdentifier(Identifier(SSymbol("IsDir"), _), _) => Some(FSEvaluator.FDir)
    case FunctionApplication(QualifiedIdentifier(Identifier(SSymbol("IsFile"), _), _), Seq(h)) =>
      Some(FSEvaluator.FFile(termToHash.getOrElse(term, "<unknown>")))
    case QualifiedIdentifier(Identifier(SSymbol("DoesNotExist"), _), _) => None
    case _ => throw Unexpected(term.toString)

  }

  def isStateError(st: ST): Boolean = {
    val Seq((_, isErr)) = smt.getValue(Seq(st.isErr))
    isErr match {
      case QualifiedIdentifier(Identifier(SSymbol("true"), Seq()), _) => true
      case QualifiedIdentifier(Identifier(SSymbol("false"), Seq()), _) => false
      case _ => throw Unexpected("value should be true or false")
    }
  }

  def stateFromTerm(st: ST): Option[State] = {
    val Seq((_, isErr)) = smt.getValue(Seq(st.isErr))
    isErr match {
      case QualifiedIdentifier(Identifier(SSymbol("true"), Seq()), _) => None
      case QualifiedIdentifier(Identifier(SSymbol("false"), Seq()), _) => {
        val (paths, terms) = st.paths.toList.unzip
        val pathVals = smt.getValue(terms).map(_._2)
        // Filtering out the Nones with .flatten
        Some(paths.zip(pathVals).map({ case (path, t) => fstateFromTerm(t).map(f => path -> f) }).flatten.toMap)
      }
      case _ => throw Unexpected("unexpected value for isErr")
    }
  }

  def exprEquals(e1: Expr, e2: Expr): Option[State] = smt.pushPop {
    // TODO(arjun): Must rule out error as the initial state
    val st = initState
    assertPathConsistency(st)
    val st1 = evalExpr(st, e1)
    val st2 = evalExpr(st, e2)
    eval(Assert(!stEquals(st1, st2)))
    if (smt.checkSat()) {
      val model: List[SExpr] = eval(GetModel()).asInstanceOf[GetModelResponseSuccess].model
      Some(stateFromTerm(st).getOrElse(throw Unexpected("error for initial state")))
    }
    else {
      None
    }
  }

  def logDiff[A,B](m1: Map[A,B], m2: Map[A,B]): Unit = {
    val keys = m1.keySet ++ m2.keySet
    for (key <- keys) {
      (m1.get(key), m2.get(key)) match {
        case (None, None) => throw Unexpected("should have been in one")
        case (Some(x), None) => logger.info(s"$key -> $x REMOVED")
        case (None, Some(x)) => logger.info(s"$key -> $x ADDED")
        case (Some(x), Some(y)) => {
          if (x != y) {
            logger.info(s"$key -> $x -> $y CHANGED")
          }
        }
      }
    }
  }

  def isIdempotent(e: Expr): Boolean = {
    val inST = initState
    val once = evalExpr(inST, e)
    val twice = evalExpr(once, e)
    eval(Assert(!stEquals(once, twice)))
    if (smt.checkSat) {
      eval(GetModel())
      false
    }
    else {
      true
    }
  }

  def isIdempotent(g: FSGraph): Boolean = smt.pushPop {
    val nodes = g.deps.topologicalSort()
    val exprs: List[Expr] = nodes.map(n => g.exprs.get(n).get)
    isIdempotent(exprs.foldRight(FSSyntax.ESkip: Expr)((e, expr) => e >> expr))
  }

  def evalTree(in: ST, t: ExecTree): Seq[(Stream[FSGraph.Key], Expr, ST)] = t match {
    case ExecTree(es, Nil, _) => {
      val path = es.map(_._1).toStream
      val expr = FSSyntax.ESeq(es.map(_._2): _*)
      Seq((path, expr, evalExpr(in, expr)))
    }
    case ExecTree(es, children, _) => {
      val path = es.map(_._1).toStream
      val expr = FSSyntax.ESeq(es.map(_._2): _*)
      val out = evalExpr(in, expr)
      children.flatMap(t_ => evalTree(out, t_).map({ case (p, e, t) => (path #::: p, expr >> e, t) }))
    }
  }

  /**
    * Produces {@code None} if {@code execTree} is deterministic.
    * Otherwise, produces {@code Some(inST, outs)}, where {@code inSt} is the
    * symbolic state that can be turned into a counter-example that witnesses
    * non-determinism and {@code outs} is a sequence of tuples that represent
    * the possible symbolic output states. Each tuple is a
    * {@code (path, expr, outSt)}, where {@code outSt} is a symbolic output
    * state and {@code path} is the sequence of resources that go from
    * {@code in} to {@code outSt}.
    *
    * This function leaves commands on the solver stack, so you typically
    * want to {@code (push)} before the call and {@code (pop)} after it.
    */
  def isDeterCore(execTree: ExecTree): Option[(ST, List[(Stream[FSGraph.Key], Expr, ST)])] = {
    val in = initState
    assertPathConsistency(in)
    val outs = evalTree(in, execTree)
    outs match {
      case Nil => throw Unexpected("no possible final state: should never happen")
      case _ :: Nil => {
        logger.info("Trivially deterministic.")
        None
      }
      case (path1, out1Expr, out1St) :: rest => {
        logger.info(s"${rest.length + 1} leaf nodes.")
        val isDet = smt.pushPop {
          eval(Assert(rest.map(out2 => !stEquals(out1St, out2._3)).or()))
          val nondet = smt.checkSat()
          !nondet
        }
        if (isDet) None else Some((in, outs.toList))
      }
    }
  }

  def isDeter(execTree: ExecTree): Boolean = smt.pushPop {
    isDeterCore(execTree).isEmpty
  }

  def diagnostic(perm: List[FSGraph.Key], graph: FSGraph): Seq[(FSGraph.Key, FSGraph.Key)] = {
    perm.tails.flatMap {
      case Nil => Seq()
      case List(_) => Seq()
      case x :: ys => {
        ys.flatMap { y =>
          val xNode = graph.deps.nodes.get(x)
          val yNode = graph.deps.nodes.get(y)
          val descendant = graph.deps.descendants(xNode).contains(yNode)
          val commutes = graph.exprs(xNode).commutesWith(graph.exprs(yNode))
          if (!descendant && !commutes) {
            Seq((y, x))
          }
          else {
            Seq()
          }
        }
      }
    }.toSeq
  }

  def isDeterDiagnostic(execTree: ExecTree): Option[Seq[(FSGraph.Key, FSGraph.Key)]] = smt.pushPop {
    isDeterCore(execTree) match {
      case None => None
      case Some((in, Nil)) => throw Unexpected("caught in inDeterCore")
      case Some((in, _ :: Nil)) => throw Unexpected("deterministic")
      case Some((in, (path1, out1Expr, out1St) :: rest)) => {
        val result = rest.findMap {
          case (path2, _, out2St) => smt.pushPop {
            eval(Assert(!stEquals(out1St, out2St)))
            if (smt.checkSat()) {
              val badPath = (if (isStateError(out1St)) path1 else path2).toList
              Some(diagnostic(badPath, execTree.graph))
            }
            else {
              None
            }
          }
        }
        assert(result.isDefined)
        result
      }
    }
  }

  def alwaysError(expr: Expr) = smt.pushPop {
    val in = initState
    assertPathConsistency(in)
    val out = evalExpr(in, expr)
    eval(Assert(!out.isErr))
    // Does there exist an input (in) that produces an output (out) that
    // is not the error state?
    !smt.checkSat()
  }

  def isDeterError(execTree: ExecTree): Boolean = smt.pushPop {
    isDeter(execTree) && alwaysError(execTree.exprs())
  }

  def free(): Unit = smt.free()

}



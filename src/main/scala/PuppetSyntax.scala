package rehearsal

object PuppetSyntax extends com.typesafe.scalalogging.LazyLogging {

  import scala.util.parsing.input.Positional
  import scalax.collection.Graph
  import scalax.collection.GraphEdge.DiEdge
  import Implicits._

  // Documentation states that include can accept:
  //   * a single class name (apache) or a single class reference (Class['apache'])
  //   * a comma separated list of class names or class references
  //   * an array of class names or class references
  //  Examples:
  //  include base::linux
  //  include Class['base::linux'] # including a class reference
  //  include base::linux, apache # including a list
  //  $my_classes = ['base::linux', 'apache']
  //  include $my_classes # including an array
  //
  //  NOTE: The parser tests only include the first of these three
  //
  // The main difference between include and require is that require
  // causes the surrounding container to have a dependency on that class.
  // That is, all of the resources in the class are guaranteed to
  // have been applied before the surrounding structure is instantiated
  // Another difference is that requiring the same class twice is actually
  // a runtime error.


  case class Attribute(name: Expr, value: Expr)
  case class Argument(id: String, default: Option[Expr]) //ignoring types for now

  sealed trait Resource extends Positional
  case class ResourceDecl(typ: String, resources: Seq[(Expr, Seq[Attribute])]) extends Resource
  case class ResourceRef(typ: String, title: Expr, attrs: Seq[Attribute]) extends Resource
  case class RCollector(typ: String, expr: RExpr) extends Resource

  sealed trait Case extends Positional
  case class CaseDefault(m: Manifest) extends Case
  case class CaseExpr(e: Expr, m: Manifest) extends Case

  sealed trait Manifest extends Positional {

    def >>(other: Manifest) = (this, other) match {
      case (MEmpty, _) => other
      case (_, MEmpty) => this
      case _ => MSeq(this, other)
    }

   def eval(): EvaluatedManifest = PuppetEval.eval(this)
  }

  case object MEmpty extends Manifest
  case class MSeq(m1: Manifest, m2: Manifest) extends Manifest
  case class MResources(resources: Seq[Resource]) extends Manifest
  case class MDefine(name: String, params: Seq[Argument], body: Manifest) extends Manifest
  case class MClass(name: String, params: Seq[Argument], inherits: Option[String], body: Manifest) extends Manifest
  case class MSet(varName: String, e: Expr) extends Manifest
  case class MCase(e: Expr, cases: Seq[Case]) extends Manifest
  case class MIte(pred: Expr, m1: Manifest, m2: Manifest) extends Manifest
  case class MInclude(es: List[Expr]) extends Manifest
  case class MRequire(e: Expr) extends Manifest
  case class MApp(name: String, args: Seq[Expr]) extends Manifest
  case class MResourceDefault(typ: String, attrs: Seq[Attribute]) extends Manifest

   // Manifests must not appear in Expr, either directly or indirectly
  sealed trait Expr extends Positional {
     def value[T](implicit extractor: Extractor[Expr, T]) = extractor(this)
  }

  case object EUndef extends Expr
  case class EStr(s: String) extends Expr
  case class ENum(n: Int) extends Expr
  case class EVar(name: String) extends Expr
  case class EBool(b: Boolean) extends Expr
  case class ENot(e: Expr) extends Expr
  case class EAnd(e1: Expr, e2: Expr) extends Expr
  case class EOr(e1: Expr, e2: Expr) extends Expr
  case class EEq(e1: Expr, e2: Expr) extends Expr
  case class ELT(n1: Expr, n2: Expr) extends Expr
  case class EMatch(e1: Expr, e2: Expr) extends Expr
  case class EIn(e1: Expr, e2: Expr) extends Expr
  case class EArray(es: Seq[Expr]) extends Expr
  case class EApp(name: String, args: Seq[Expr]) extends Expr
  case class ERegex(regex: String) extends Expr
  case class ECond(test: Expr, truePart: Expr, falsePart: Expr) extends Expr
  case class EResourceRef(typ: String, title: Expr) extends Expr

  sealed trait RExpr extends Positional
  case class REAttrEqual(attr: String, value: Expr) extends RExpr
  case class REAnd(e1: RExpr, e2: RExpr) extends RExpr
  case class REOr(e1: RExpr, e2: RExpr) extends RExpr
  case class RENot(e: RExpr) extends RExpr

  // Our representation of fully evaluataed manifests, where nodes are primitive resources.
  case class EvaluatedManifest(ress: Map[FSGraph.Key, ResourceVal], deps: Graph[FSGraph.Key, DiEdge]) {
    def resourceGraph(): ResourceGraph = ResourceGraph(ress.mapValues(x => ResourceSemantics.compile(x)).view.force, deps)

  }

  case class ResourceVal(typ: String, title: String, attrs: Map[String, Expr]) {
    val node: FSGraph.Key = Node(typ, title)
  }

  val primTypes =  Set("file", "package", "user", "group", "service",
    "ssh_authorized_key", "augeas", "notify", "cron", "host")

  case class Node(typ: String, title: String) extends FSGraph.Key {
    lazy val isPrimitiveType = primTypes.contains(typ)
  }

  case class ResourceGraph(ress: Map[FSGraph.Key, ResourceModel.Res], deps: Graph[FSGraph.Key, DiEdge]) {

    def fsGraph(distro: String): FSGraph = {
      FSGraph(ress.mapValues(_.compile(distro)).view.force, deps)
    }
  }

  type FileScriptGraph = FSGraph


}

package rehearsal

private object PuppetEval {

  import PuppetSyntax._
  import scalax.collection.Graph
  import scalax.collection.GraphEdge.DiEdge
  import edu.umass.cs.extras.Implicits._
  import Implicits._

  object StringInterpolator {

    def interpolate(eval: Expr => Expr, str: String): Expr = {
      val strs = str.split("""\$""")
      EStr(strs(0) + strs.toList.drop(1).map(helper(eval)).mkString(""))
    }

    def helper(eval: Expr => Expr)(str: String): String = {
      val tokens = str.split("""\s+""").toList
      checkBraces(eval, tokens(0)) + tokens.drop(1).mkString("")
    }

    def checkBraces(eval: Expr => Expr, str: String): String = {
      str.indexOf("{") match {
        case 0 => {
          val ix = str.indexOf("}")
          val expr = str.substring(1, ix)
          val rest = str.substring(ix+1, str.length)
          evaluate(eval, expr) + rest
        }
        case _ => evaluate(eval, str)
      }
    }

    // TODO(arjun): We should really parse in the parser
    def evaluate(eval: Expr => Expr, str: String): String = {
      val strPrime = if(str.charAt(0) != '$') "$" + str else str
      PuppetParser.parseExpr(strPrime) match {
        case Some(expr) => eval(expr) match {
          case EStr(s) => s
          case _ => throw EvalError(s"None string expression evaluated during string interpolation: $expr")
        }
        case m => throw EvalError(s"Could not parse interpolated expression: $m")
      }
    }

  }

  def assignedVars(manifest: Manifest): Set[String] = manifest match {
    case MEmpty => Set.empty
    case MSeq(m1, m2) => assignedVars(m1) union assignedVars(m2)
    case MResources(_) => Set.empty
    case MDefine(_, _, _) => Set.empty
    case MClass(_, _, _, _) => Set.empty
    case MSet(x, _) => Set(x)
    case MCase(_, cases) => cases.map(acase => acase match {
      case CaseDefault(m) => assignedVars(m)
      case CaseExpr(_, m) => assignedVars(m)
    }).foldLeft(Set[String]())(_ union _)
    case MIte(_, m1, m2) => assignedVars(m1) union assignedVars(m2)
    case MInclude(_) => Set.empty
    case MRequire(_) => Set.empty
    case MApp(_, _) => Set.empty
    case MResourceDefault(_, _) => Set.empty
  }

  /**
    * A reference cell that may be empty or may contain a value. The
    * value can only be set once.
    *
    * @param name A name, used for error messages
    */
  class Ref(val name: String, parent: Option[Ref] = None) {

    private var v: Option[Expr] = None

    def value: Expr = v match {
      // HACK: Puppet semantics says that uninitialized variables are set to
      // the value undef. But, this helps us catch other bugs. So, any
      // manifest that relies on reading undef needs to be modified to
      // initialize variables to undef
      case None => parent match {
        case None => throw EvalError(s"variable $name is undefined")
        case Some(parent) => parent.value
      }
      case Some(x) => x
    }

    def value_=(x: Expr): Unit = v match {
       // HACK: Necessary due to the hack in the getter.
      case Some(EUndef) => v = Some(x)
      case Some(_) => throw EvalError(s"variable $name is already set")
      case None => v = Some(x)
    }
  }

  object Ref {
    def apply(name: String, value: Expr) = {
      val x = new Ref(name)
      x.value = value
      x
    }
  }

  type Store = Map[String, Ref]

  case class AType(
    isClass: Boolean,
    store: Store,
    defaults: Map[String, Map[String, Expr]],
    args: Map[String, Option[Expr]],
    body: Manifest)

  case class AInstance(
    typ: String,
    title: String,
    args: Map[String, Expr],
    instance: Datalog.Term)

  case class AResource(typ: String, title: String)

  private class PuppetEval {
    val types = scala.collection.mutable.Map[String, AType]()

    val resourceToTerm = new Datalog.ToTerm[Node]("resource")
    val valueToTerm = new Datalog.ToTerm[Expr]("value")
    val datalog = new Datalog.Evaluator()

    val nodeSets: Iterator[Datalog.Term] = Stream.from(0).map(n => Datalog.Lit(s"set$n")).iterator

    // A hack to implement the defined predicate
    val definedResources = scala.collection.mutable.Set[Node]()

    import Datalog._

    // If Src is within an instance I and the edge I -> Dst exists, then
    // add the edge Src -> Dst
    datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("ininstance", List(Var("Src"), Var("I"))),
        Fact("edge", List(Var("I"), Var("Dst")))))

    // Similar to the rule above, but with Dst is within an instance I.
    datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("ininstance", List(Var("Dst"), Var("I"))),
        Fact("edge", List(Var("Src"), Var("I")))))

    // Anchors
    datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("type", List(Var("A"), valueToTerm(EStr("anchor")))),
        Fact("edge", List(Var("Src"), Var("A"))),
        Fact("edge", List(Var("A"), Var("Dst")))))

    // Stages
    datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("instage", List(Var("Src"), Var("Stage"))),
        Fact("edge", List(Var("Stage"), Var("Dst")))))
    datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("ininstance", List(Var("Dst"), Var("Stage"))),
        Fact("edge", List(Var("Src"), Var("Stage")))))

    // Edges between primitive resources
    datalog.rule(Fact("real_edge", List(Var("Src"), Var("Dst"))),
      List(
        Fact("edge", List(Var("Src"), Var("Dst"))),
        Fact("resource", List(Var("Src"))),
        Fact("resource", List(Var("Dst")))))


    def edgeFact(src: Term, dst: Term) = Fact("edge", List(src, dst))
    def typeFact(node: Term, typ: Term) = Fact("type", List(node, typ))
    def titleFact(node: Term, title: Term) = Fact("title", List(node, title))
    def attrFact(node: Term, name: Term, value: Term) =
      Fact("attribute", List(node, name, value))

    // Auto-require rules (except between files and their parents)
    datalog.rule(
      edgeFact(Var("User"), Var("File")),
      List(
        typeFact(Var("User"), valueToTerm(EStr("user"))),
        typeFact(Var("File"), valueToTerm(EStr("file"))),
        attrFact(Var("File"), valueToTerm(EStr("owner")), Var("X")),
        titleFact(Var("User"), Var("X"))))

    datalog.rule(
      edgeFact(Var("User"), Var("Cron")),
      List(
        typeFact(Var("User"), valueToTerm(EStr("user"))),
        typeFact(Var("Cron"), valueToTerm(EStr("cron"))),
        attrFact(Var("Cron"), valueToTerm(EStr("user")), Var("X")),
        titleFact(Var("User"), Var("X"))))

    datalog.rule(
      edgeFact(Var("File"), Var("Package")),
      List(
        typeFact(Var("File"), valueToTerm(EStr("file"))),
        typeFact(Var("Package"), valueToTerm(EStr("package"))),
        attrFact(Var("Package"), valueToTerm(EStr("source")), Var("X")),
        titleFact(Var("File"), Var("X"))))

    datalog.rule(
      edgeFact(Var("User"), Var("Ssh")),
      List(
        typeFact(Var("User"), valueToTerm(EStr("user"))),
        typeFact(Var("Ssh"), valueToTerm(EStr("ssh_authorized_key"))),
        attrFact(Var("Ssh"), valueToTerm(EStr("user")), Var("X")),
        titleFact(Var("User"), Var("X"))))

    var newInstances = scala.collection.mutable.Stack[AInstance]()

    def evalTitle(store: Store, titleExpr: Expr): String = {
      evalExpr(store, titleExpr) match {
        case EStr(title) => title
        case v => throw EvalError(s"title should be a string, but got $v ${titleExpr.pos}")
      }
    }

    def evalTitles(store: Store, titleExpr: Expr): Seq[String] = {
      evalExpr(store, titleExpr) match {
        case EStr(title) => Seq(title)
        // Re-evaling is unnecessary, but harmless
        case EArray(titles) => titles.map(x => evalTitle(store, x))
        case v => throw EvalError(s"title should be a string or an array, but got $v{titleExpr.pos}")
      }
    }

    // Helps implement conditionals. "Truthy" values are mapped to Scala's true
    // and "falsy" values are mapped to Scala's false.
    def evalBool(store: Store, expr: Expr): Boolean = evalExpr(store, expr) match {
      case EBool(true) => true
      case EBool(false) => false
      case EStr(_) => true
      case EUndef => false
      case v => throw EvalError(s"predicate evaluated to $v")
    }

    def evalExpr(store: Store, expr: Expr): Expr = expr match {
      case EUndef => EUndef
      case ENum(n) => ENum(n)
      case EStr(str) => StringInterpolator.interpolate(e => evalExpr(store, e), str)
      case EBool(b) => EBool(b)
      case ERegex(r) => ERegex(r)
      case EResourceRef(typ, title) => EResourceRef(typ.toLowerCase, evalExpr(store, title))
      case EEq(e1, e2) => EBool(evalExpr(store, e1) == evalExpr(store, e2))
      case ELT(e1, e2) => (evalExpr(store, e1), evalExpr(store, e2)) match {
        case (ENum(n1), ENum(n2)) => EBool(n1 < n2)
        case _ => throw EvalError(s"expected args to LT to evaluate to Nums")
      }

      case ENot(e) => EBool(!evalBool(store, e))
      case EAnd(e1, e2) => EBool(evalBool(store, e1) && evalBool(store, e2))
      case EOr(e1, e2) => EBool(evalBool(store, e1) || evalBool(store, e2))
      case EVar(x) => store.get(x) match {
          // TODO(arjun): strictly speaking, should produce undefined, but this
          // helps catch bugs
        case None => throw EvalError(s"variable $x is undefined")
        case Some(v) => v.value
      }
      case EArray(es) => EArray(es.map(e => evalExpr(store, e)))
      case EApp("template", Seq(e)) => evalExpr(store, e) match {
        // TODO(arjun): This is bogus. It is supposed to use filename as a
        // template string. The file contents have patterns that refer to variables
        // in the environment.
        case EStr(filename) => EStr(filename)
        case _ => throw EvalError("template function expects a string argument")
      }
      case EApp("defined", Seq(rref)) => evalExpr(store, rref) match {
        case EResourceRef(typ, EStr(title)) => EBool(definedResources.contains(Node(typ, title)))
        case v => throw EvalError(s"expected resource reference as argument to defined")
      }
      case EApp(f, args) => ???
      case ECond(e1, e2, e3) => evalBool(store, e1) match {
        case true => evalExpr(store, e2)
        case false => evalExpr(store, e3)
      }
      case EMatch(e1, e2) => (evalExpr(store, e1), evalExpr(store, e2)) match {
        case (EStr(s), ERegex(r)) => {
          val pat = r.r
          s match {
            case pat(_) => EBool(true)
            case _ => EBool(false)
          }
        }
        case _ => throw EvalError(s"expected match to find a string on the LHS and a regex on the RHS")
      }
      case _ => ???
    }

    def resourceRefs(e: Expr): Seq[Datalog.Term] = e match {
      case EResourceRef(typ, EStr(title)) => Seq(resourceToTerm(Node(typ, title)))
      case EArray(seq) => seq.map(resourceRefs).flatten
      case _ => throw EvalError(s"expected resource reference, got $e ${e.pos}")
    }

    def evalRExpr(set: Datalog.Term, pred: RExpr): List[Datalog.Fact] = pred match {
       // NOTE: The value does not need to be evaluated, since they are restricted
       // to being syntactic values.
      case REAttrEqual(attr, value) =>
        List(Fact("attribute", List(Var("X"), valueToTerm(EStr(attr)), valueToTerm(value))))
      case REAnd(e1, e2) => evalRExpr(set, e1) ++ evalRExpr(set, e2)
      case REOr(e1, e2) => {
        val newSet = nodeSets.next()
        datalog.rule(Fact("in_set", List(newSet, Var("X"))),
          evalRExpr(newSet, e1))
        datalog.rule(Fact("in_set", List(newSet, Var("X"))),
          evalRExpr(newSet, e2))
        List(Fact("in_set", List(newSet, Var("X"))))
      }
      // TODO(arjun): Negation, yuck
      case RENot(_) => ???
    }

    def autorequire(thisNode: Datalog.Term,
      typ: String, title: String, attrs: Map[String, Expr]): Unit = {
      val deps = typ match {
        case "file" => {
          val path = attrs.get("path").map(_.value[String].get).getOrElse(title)
          val parent = path.toPath.getParent
          // If the path is / or is one of the bogus paths we use in testing, we may get an exception.
          if (parent != null) List(Node("file", parent.toString)) else Nil
        }
        case _ => Nil
      }
      for (src <- deps) {
        datalog.fact("edge", List(resourceToTerm(src), thisNode))
      }
    }

    def evalResource(store: Store, instance: Datalog.Term, resource: Resource): Datalog.Term = {
      val set = nodeSets.next()
      resource match {
        case ResourceRef(typ, titleExpr, Seq()) => {
          datalog.fact("in_set", List(set, resourceToTerm(Node(typ.toLowerCase, evalTitle(store, titleExpr)))))
          set
        }
        case RCollector(typ, pred) => {
          // TODO(arjun): Propagate type to keep sub-sets small?
          datalog.rule(Fact("in_set", List(set, Var("X"))),
              Fact("type", List(Var("X"), valueToTerm(EStr(typ.toLowerCase)))) ::
              evalRExpr(set, pred))
          set
        }
        case ResourceDecl(typ, alist) => {
          val alist_ = alist.flatMap({ case (titleExpr, attrs) =>
            evalTitles(store, titleExpr).map(title => title -> attrs) })
          for ((title, attrs) <- alist_) {
            val node = Node(typ, title)
            if (definedResources.contains(node)) {
              throw EvalError(s"resource $node is already defined")
            }
            definedResources += node
            val res = resourceToTerm(node)
            datalog.fact("in_set", List(set, res))
            val attrValues = attrs.map(attr => attr.name.value[String].get -> evalExpr(store, attr.value)).toMap
            datalog.fact("type", List(res, valueToTerm(EStr(typ))))
            datalog.fact("title", List(res, valueToTerm(EStr(title))))
            autorequire(res, typ, title, attrValues)
            for ((name, value) <- attrValues) {
              name match {
                case "alias" => {
                  val alias = Node(typ, evalTitle(store, value))
                  resourceToTerm.alias(alias, Node(typ, title))
                }
                case "before" => {
                  for (dst <- resourceRefs(value)) {
                    datalog.fact("edge", List(res, dst))
                  }
                }
                case "require" => {
                  for (src <- resourceRefs(value)) {
                    datalog.fact("edge", List(src, res))
                  }
                }
                case "notify" => {
                  for (dst <- resourceRefs(value)) {
                    datalog.fact("edge", List(res, dst))
                  }
                }
                case "subscribe" => {
                  for (src <- resourceRefs(value)) {
                    datalog.fact("edge", List(src, res))
                  }
                }
                case "stage" => {
                  if (typ != "class") {
                    throw EvalError("stages only apply to classes")
                  }
                  // TODO(arjun): Better error when stage-value is not a string
                  datalog.fact("instage", List(res, resourceToTerm(Node("stage", value.value[String].get))))
                }
                case name => {
                  datalog.fact("attribute", List(res, valueToTerm(EStr(name)), valueToTerm(value)))
                }
              }
            }
            datalog.fact("ininstance", List(res, instance))
            if (PuppetSyntax.primTypes.contains(typ)) {
              datalog.fact("resource", List(res))
            }
            else if (typ == "anchor") {
              // Do nothing
            }
            else if (typ == "stage") {
             // Do nothing
            }
            else {
              val anInstance = AInstance(typ, title, attrValues, res)
              newInstances.push(anInstance)
            }
          }
          set
        }
      }
    }

    def evalManifest(store: Store,
                     instance: Datalog.Term,
                     manifest: Manifest): Unit = manifest match {
      case MEmpty => ()
      case MSeq(m1, m2) => {
        evalManifest(store, instance, m1)
        evalManifest(store, instance, m2)
      }
      case MResources(resources) => {
        val aseq = resources.map(r => evalResource(store, instance, r))
        for ((src, dst) <- aseq.sliding2) {
          datalog.rule(Fact("edge", List(Var("Src"), Var("Dst"))),
            List(Fact("in_set", List(src, Var("Src"))),
                 Fact("in_set", List(dst, Var("Dst")))))
        }
      }
      case MDefine(f, args, body) => {
        if (types.contains(f)) {
          throw EvalError(s"$f is already defined as a type or class")
        }
        else {
          val atype = AType(isClass = false,
            store = store,
            defaults = Map(),
            // TODO(arjun): ensure no duplicate identifiers
            args = args.map(arg => arg.id -> arg.default).toMap,
            body = body)
          types += f -> atype
        }
      }
      case MClass(f, args, _, body) => {
        if (types.contains(f)) {
          throw EvalError(s"$f is already defined as a type or class")
        }
        else {
          val atype = AType(isClass = true,
            store = store,
            defaults = Map(),
            // TODO(arjun): ensure no duplicate identifiers
            args = args.map(arg => arg.id -> arg.default).toMap,
            body = body)
          types += f -> atype
        }
      }
      case MSet(x, e) => {
        assert(store.contains(x), s"variable $x should be in the store")
        store(x).value = evalExpr(store, e)
      }
      case MIte(e, m1, m2) => evalBool(store, e) match {
        case true => evalManifest(store, instance, m1)
        case false => evalManifest(store, instance, m2)
      }
      case MInclude(titles) => {
        for (title <- titles.map(e => evalTitle(store, e))) {
          val node = Node("class", title)
          if (definedResources.contains(node) == false) {
            definedResources += node
            val res = resourceToTerm(Node("class", title))
            val anInstance = AInstance("class", title, Map(), res)
            newInstances.push(anInstance)
          }
        }
      }
      case MApp("fail", Seq(str)) => evalExpr(store, str) match {
        // TODO(arjun): Different type of error
        case EStr(str) => throw EvalError(s"user-defined failure: $str")
        case v => throw EvalError(s"expected string argument, got $v ${manifest.pos}")
      }
      case MApp(f, _) => ???
      case MCase(e, cases) => {
        val v = evalExpr(store, e)
        cases.find(c => matchCase(store, v, c)) match {
          case None => ()
          case Some(CaseDefault(m)) => evalManifest(store, instance, m)
          case Some(CaseExpr(_, m)) => evalManifest(store, instance, m)
        }
      }
      case MResourceDefault(typ, attrExprs) => {
        for (attr <- attrExprs) {
          val name = valueToTerm(attr.name)
          val value = valueToTerm(evalExpr(store, attr.value))
          datalog.rule(Fact("attribute", List(Var("R"), name, value)),
            List(
              Fact("ininstance", List(Var("R"), instance)),
              Fact("type", List(Var("R"), valueToTerm(EStr(typ.toLowerCase))))))
        }
      }
    }

    def matchCase(store: Store, value: Expr, aCase: Case): Boolean = aCase match {
      case CaseDefault(_) => true
      case CaseExpr(e, _) => evalExpr(store, e) == value
    }

    def eval(manifest: Manifest): Unit = {
      val instances = Stream.from(0).toIterator
      val instanceToTerm = new Datalog.ToTerm[Int]("instance")
      val topLevel = instanceToTerm(instances.next)
      val topStore = assignedVars(manifest).map(x => (x, new Ref(x))).toMap

      evalManifest(topStore, topLevel, manifest)
      while (newInstances.isEmpty == false) {
        val AInstance(typ, title, actuals, instance) = newInstances.pop()
        val AType(isClass, enclosingStore, defaults, formals, body) =
          if (typ == "class") {
            types.getOrElse(title, throw EvalError(s"class $title not found"))
          }
          else {
            types.getOrElse(typ, throw EvalError(s"type $typ not found"))
          }
        val interStore = enclosingStore ++
          Map("title" -> Ref("title", EStr(title)),
            "name" -> Ref("name", actuals.getOrElse("name", EStr(title))))
        val args = formals.map({
          case ("title", _) => "title" -> Ref("title", EStr(title))
          case (name, None) => name -> Ref(name, actuals.getOrElse(name, throw EvalError(s"argument $name missing")))
          case (name, Some(default)) => name -> Ref(name, actuals.getOrElse(name, evalExpr(interStore, default)))
        }).toMap
        val store = {
          val withArgs = interStore ++ args
          val avars = assignedVars(body)
           withArgs.combine(avars.map(name => name -> name).toMap)(
             (enclosing, local) => new Ref(local, Some(enclosing)),
             parent => new Ref(parent.name, Some(parent)),
             local => new Ref(local, None))
        }
        evalManifest(store, instance, body)
      }
    }
  }

  def eval(manifest: Manifest): EvaluatedManifest = {
    import Datalog._
    val evaluator = new PuppetEval
    import evaluator._
    evaluator.eval(manifest)


    datalog.query(Fact("resource", List(Var("X"))))
    datalog.query(Fact("attribute", List(Var("X"), Var("Y"), Var("Z"))))
    datalog.query(Fact("real_edge", List(Var("X"), Var("Y"))))
    val facts = evaluator.datalog.eval()
    val (resourceFacts, restFacts) = facts.span(fact => fact.pred == "resource")
    val (attrFacts, edgeFacts) = restFacts.span(fact => fact.pred == "attribute")

    def loadAttr(fact: Fact): (Node, String, Expr) = fact match {
      case Fact("attribute", List(resource, attrName, attrValue)) => {
        (resourceToTerm.invert(resource),
          valueToTerm.invert(attrName).value[String].get,
            valueToTerm.invert(attrValue))
      }
    }

    def mkResourceVal(node: Node, attrsWithNode: List[(Node, String, Expr)]): (FSGraph.Key, ResourceVal) = {

      val attrs = attrsWithNode.map(tuple => (tuple._2, tuple._3)).toMap
      node -> ResourceVal(node.typ, node.title, attrs)
    }

    def loadEdge(fact: Fact) = fact match {
      case Fact("real_edge", List(src, dst)) =>
        DiEdge[FSGraph.Key](resourceToTerm.invert(src), resourceToTerm.invert(dst))
    }

    val attrByResource = attrFacts.map(loadAttr).groupBy(_._1)
    val nodes  = resourceFacts.map(r => evaluator.resourceToTerm.invert(r.terms(0)))
    val resources = nodes.map(node => mkResourceVal(node, attrByResource.getOrElse(node, Nil))).toMap

    val edges = edgeFacts.map(loadEdge)
    val g = Graph.from(nodes, edges)
    if (g.isAcyclic == false) {
      throw EvalError("dependency cycle found")
    }
    EvaluatedManifest(resources, g)
  }

}

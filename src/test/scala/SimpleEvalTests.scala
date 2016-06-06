class SimpleEvalTests extends org.scalatest.FunSuite {

	import rehearsal._
	import PuppetSyntax._
	import PuppetParser.parse
	import java.nio.file._
	import scala.collection.JavaConversions._
  import scalax.collection.Graph
  import scalax.collection.Graph._
  import scalax.collection.GraphPredef._
  import scalax.collection.GraphEdge._
  import scalax.collection.edge.Implicits._

	for (path <- Files.newDirectoryStream(Paths.get("parser-tests/good"))) {

		test(path.toString) {
			val EvaluatedManifest(resources, deps) = PuppetParser.parseFile(path.toString).eval()
			if (deps.nodes.size == 0) {
				info("No resources found -- a trivial test")
			}
			for (k <- deps.nodes) {
        val node = k.value.asInstanceOf[Node]
				assert(node.isPrimitiveType, s"${node.value.typ} not elaborated")
			}
		}

	}

	test("simple before relationship") {

		val EvaluatedManifest(r, g) = parse("""
      file{"A":
        before => File["B"]
      }
      file{"B": }
    """).eval()

    assert(r.size == 2)
    assert(g == Graph(Node("file", "A") ~> Node("file", "B")))

	}

	test("require relationship with an array") {

		val EvaluatedManifest(r, g) = parse("""
      file{"A":
        require => [File["B"], File["C"]]
      }
      file{"B": }
      file{"C": }
    """).eval()

    assert(r.size == 3)
    assert(g == Graph(Node("file", "B") ~> Node("file", "A"),
                      Node("file", "C") ~> Node("file", "A")))
	}

	test("eval-expandAll 1") {
		val prog = """
      define foo {
        file{"foofile": }
      }
			define fun($a, $b){
				foo { '/home':
					require => $a,
					before => $b
				}
			}
			fun {'instance':
				a => File["A"],
				b => File["B"]
			}
			file{"A": }
			file{"B": }"""
		val EvaluatedManifest(r, g) = parse(prog).eval()
		assert(r.size == 3)
		assert(g == Graph(Node("file", "A") ~> Node("file", "foofile"),
		                 Node("file", "foofile") ~> Node("file", "B")))
	}

  test("expandAll: 2 defines") {
    val prog = """
      define foo {
        file{"foofile": }
      }

      define bar {
        file{"barfile": }
      }

			define funOne($a, $b){
				foo { "1":
					require => $a,
					before => $b
				}
			}
			define funTwo($a){
				bar { "2": attr => $a }
			}
			funOne { "i1":
				a => File["apple"],
				b => File["banana"]
			}
			funTwo { "i2": a => "A" }
			file { "apple": }
			file { "banana": }"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 4)
    assert(g == Graph(Node("file", "apple") ~> Node("file", "foofile"),
                      Node("file", "foofile") ~> Node("file", "banana"),
                      Node("file", "barfile")))
  }

  test("expandAll - 2 instances") {
    val prog = """
			f { "instance":
				a => "one",
				b => "two",
				c => true
			}
			define f($a, $b, $c){
				if $c {
					file { "1": content => $a }
				}else{
					file { "2": content => $b }
				}
			}
			f { "instance2":
				a => "purple",
				b => "yellow",
				c => false
			}"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 2)
    assert(g == Graph(Node("file", "1"), Node("file", "2")))
  }

  test("expandAll - instance in define") {
    val prog = """
			define f($a, $b, $c){
				if $c {
					file { "1": content => $a }
				}else{
					file { "2": content => $b }
				}
			}
			define g($pred){
				f { "instance1":
					a => "purple",
					b => "yellow",
					c => $pred
				}
			}
			g { "instance2":
				pred => true
			}"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 1)
    assert(g == Graph(Node("file", "1")))
  }

  test("edges with arrays") {
    val prog = """
			file { "/usr/rian/foo": }
			file { "/usr/rian/bar": }
			file { "/": }
			file { "/usr/": }
			file { "/usr/rian/":
				before => [File["/usr/rian/foo"], File["/usr/rian/bar"]],
				require => [File["/"], File["/usr/"]]
			}"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 5)
    assert(g == Graph(Node("file", "/usr/rian/") ~> Node("file", "/usr/rian/foo"),
                      Node("file", "/usr/rian/") ~> Node("file", "/usr/rian/bar"),
                      Node("file", "/") ~> Node("file", "/usr/rian/"),
                      Node("file", "/usr/") ~> Node("file", "/usr/rian/")))
  }

  test("expand twice") {
    val prog = """
      define hello($a) {
        file{$a: }
      }
			define fun(){
				hello { "foo": a => "the-filename" }
			}
			fun { 'i': }"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 1)
    assert(g == Graph(Node("file", "the-filename")))
  }

  test("Simple cron job") {
    val m =
      """
        cron{"task": command => "/usr/bin/backup", hour => 2 }
      """
    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(g.deps.nodes.size == 1)
  }

  test("Simple group job") {
    val m =
      """
        cron{"task": command => "/usr/bin/backup", hour => 2 }
      """
    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(g.deps.nodes.size == 1)
  }
  test("edges with singleton arrays") {
    val prog = """
			file { "/usr/rian/foo": }
			file { "/": }
			file { "/usr/rian/":
				before => [File["/usr/rian/foo"]],
				require => [File["/"]]
			}"""
    val EvaluatedManifest(r, g) = parse(prog).eval()
    assert(r.size == 3)
    assert(g == Graph(Node("file", "/usr/rian/") ~> Node("file", "/usr/rian/foo"),
                      Node("file", "/") ~> Node("file", "/usr/rian/")))
  }

  test("default resources"){
  	val prog = """
  		File{
  			content => "default",
  			ensure => present
  		}

      file{ "file1": }
  	"""
	  val res = """
			file{ "file1":
				content => "default",
				ensure => present
			}
		"""
		assert(parse(prog).eval() == parse(res).eval())
  }

  test("array of titles") {
    val prog = """
      file{[ "/x", "/y", "/z" ]: ensure => present }
      """
    assert(parse(prog).eval.ress.size == 3)
  }

  test("resource collector (simple)") {
    val prog ="""
      User['arjun'] -> File<| owner = 'arjun' |>

      user{"arjun": }
      file{"/home/arjun/x": owner => arjun }
      file{"/home/arjun/y": owner => arjun }
      """
    val deps = parse(prog).eval.deps
    assert(deps.contains(Node("user", "arjun") ~> Node("file", "/home/arjun/x")))
    assert(deps.contains(Node("user", "arjun") ~> Node("file", "/home/arjun/y")))
    assert(deps.edges.size == 2)
  }

  test("resource collector (conjunction)") {
    val prog = """
      User['arjunx'] -> File<| owner = 'arjun' and mode = 0600 |>

      user{"arjunx": }
      file{"/home/arjun/x": owner => arjun, mode => 0600 }
      file{"/home/arjun/y": owner => arjun }
              """
    val deps = parse(prog).eval.deps
    assert(deps.contains(Node("user", "arjunx") ~>Node("file", "/home/arjun/x") ))
    assert(deps.edges.size == 1)
  }

  test("resource collector (disjunction)") {
    val prog ="""
      User['arjun'] -> File<| owner = 'arjun' or owner = 'rachit' |>

      user{"arjun": }
      file{"/home/arjun/x": owner => arjun }
      file{"/home/arjun/y": owner => rachit }
              """
    val deps = parse(prog).eval.deps
    assert(deps.contains(Node("user", "arjun") ~> Node("file", "/home/arjun/x")))
    assert(deps.contains(Node("user", "arjun") ~> Node("file", "/home/arjun/y")))
    assert(deps.edges.size == 2)
  }

  test("resource collector (nested)") {
    val prog = """
      User['arjunx'] -> File<| (owner = 'arjun' or owner = 'rachit') and mode = 0600 |>

      user{"arjunx": }
      file{"/home/arjun/x": owner => arjun }
      file{"/home/arjun/y": owner => rachit, mode => 0600 }
    """
    val deps = parse(prog).eval.deps
    assert(deps.contains(Node("user", "arjunx") ~> Node("file", "/home/arjun/y")))
    assert(deps.edges.size == 1)
  }



}

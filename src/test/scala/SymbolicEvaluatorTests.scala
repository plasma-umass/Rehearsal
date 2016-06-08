class SymbolicEvaluatorTests extends FunSuitePlus {

  import edu.umass.cs.extras.Implicits._
  import rehearsal._
  import TestImplicits._
  import FSSyntax._
  import scalax.collection.Graph
  import scalax.collection.GraphEdge.DiEdge
  import rehearsal.Implicits._
  import java.nio.file.Paths
  import SymbolicEvaluator.predEquals

  def comprehensiveIsDet(original: FSGraph): Boolean = {
    val pruned = original.pruneWrites().toExecTree().isDeterministic()
    val expected = original.toExecTree().isDeterministic()
    assert(pruned == expected, "pruning changed result of determinism")
    expected
  }

  def comprehensiveIdem(original: FSGraph): Boolean = {
    val expr = original.expr()
    val expected =  expr.isIdempotent()
    assert(expr.isIdempotent() == expected, "pruning changed the result of idempotence")
    expected
  }

  test("Manifest that creates a user account and file in her home directory (non-deterministic)") {
    val m =
      """
        package{'vim':
          ensure => present
        }

        file{'/home/carol/.vimrc':
          content => "syntax on"
        }

        user{'carol':
          ensure => present,
          managehome => true
        }
        """

    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == false)
  }

  test("Manifest that creates a user account and file in her home directory (deterministic)") {
    val m =
      """
        package{'vim':
          ensure => present
        }

        file{'/home/carol/.vimrc':
          content => "syntax on"
        }

        user{'carol':
          ensure => present,
          managehome => true
        }

        User['carol'] -> File['/home/carol/.vimrc']
      """

    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == true)
  }

  test("Manifest that configures Apache web server (non-deterministic)") {
    val m =
      """
        file {"/etc/apache2/sites-available/000-default.conf":
          content => "dummy config",
        }
        package{"apache2": ensure => present }
      """

    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == false)
  }

  test("Manifest that configures Apache web server (deterministic)") {
    val m =
      """
        file {"/etc/apache2/sites-available/000-default.conf":
          content => "dummy config",
          require => Package["apache2"]
        }
        package{"apache2": ensure => present }
      """

    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == true)
  }

  test("Manifest with a user-account abstraction (non-deterministic)") {
    // Since we already test the body of myuser, this test only shows
    // that defined-types work correctly.
    val m =
      """
        define myuser($title) {
          user {"$title":
            ensure => present,
            managehome => true
          }
          file {"/home/${title}/.vimrc":
            content => "syntax on"
          }
        }
        myuser {"alice": }
        myuser {"carol": }
      """

    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert (comprehensiveIsDet(g) == false)
  }

  test("Manifest with a user-account abstraction (deterministic)") {
    // Since we already test the body of myuser, this test only shows
    // that defined-types work correctly.
    val m =
      """
        define myuser($title) {
          user {"$title":
            ensure => present,
            managehome => true
          }
          file {"/home/${title}/.vimrc":
            content => "syntax on"
          }
          User["$title"] -> File["/home/${title}/.vimrc"]
        }
        myuser {"alice": }
        myuser {"carol": }
      """
    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert (comprehensiveIsDet(g) == true)
  }

  test("Manifest with two modules that cannot be composed together") {
    val m =
      """
      define cpp() {
        if !defined(Package['m4']) {
          package {'m4': ensure => present }
        }
        if !defined(Package['make']) {
          package {'make': ensure => present }
        }
        package {'gcc': ensure => present }
        Package['m4'] -> Package['make']
        Package['make'] -> Package['gcc']
      }

      define ocaml() {
        if !defined(Package['m4']) {
          package {'m4': ensure => present }
        }
        if !defined(Package['make']) {
          package {'make': ensure => present }
        }
        package {'ocaml': ensure => present }
        Package['make'] -> Package['m4']
        Package['m4'] -> Package['ocaml']
      }

      cpp{"mycpp": }
      ocaml{"myocaml": }
      """
    intercept[EvalError] {
      PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    }
  }

  test("Non-idempotence with just two files") {
    val m =
      """
        file {"/dst": source => "/src" }
        file {"/src": ensure => absent }
        File["/dst"] -> File["/src"]
      """
    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == true)
    assert(comprehensiveIdem(g) == false)
  }

  test("User manages /etc/hosts manually and with Puppet (non-deterministic)") {
    val m =
      """
        host {"umass.edu": ip => "localhost"}
        file{"/etc/hosts": content => "my hosts"}
      """
    val g = PuppetParser.parse(m).eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(comprehensiveIsDet(g) == false)
  }

  test("simple equality") {
    val x = testFileState(Paths.get("/usr"), IsFile)
    assert(predEquals(x, x))
  }

  test("basic predicates") {
    val x = testFileState(Paths.get("/usr"), IsFile)
    val y = testFileState(Paths.get("/usr"), IsDir)
    assert(predEquals(x, y) == false)
  }

  test("program equivalence") {
    val x = createFile(Paths.get("/usr"), "astring")
    assert(x equivalentTo x)
  }


  test("program equivalence 2") {
    val x = createFile(Paths.get("/usr"), "astring")
    val y = createFile(Paths.get("/lib"), "astring")
    assert(seq(x, y).equivalentTo(seq(y, x)))
  }

  test("program equivalence 3") {
    val x = createFile(Paths.get("/usr/bin"), "astring")
    val y = createFile(Paths.get("/usr"), "astring")
    assert(x.equivalentTo(y) == false)
  }

  test("program equivalence 4 - Mkdir"){
    val x = mkdir(Paths.get("/usr"))
    assert(x equivalentTo x)
  }

  test("program equivalence 5 - Mkdir") {
    val x = mkdir(Paths.get("/usr"))
    val y = createFile(Paths.get("/usr/bin"), "astring")
    assert(seq(x, y).equivalentTo(seq(y, x)) == false)
  }

  test("program equivalence 6 - Mkdir"){
    val x = mkdir(Paths.get("/usr"))
    val y = mkdir(Paths.get("/lib"))
    assert(seq(x, y).equivalentTo(seq(y, x)))
  }

  test("program equivalence 7 - Rm"){
    val y = createFile(Paths.get("/usr"), "astring")
    val x = rm(Paths.get("/usr"))
    assert(seq(y, x).equivalentTo(seq(x, y)) == false)
  }

  test("program equivalence 8 - Rm"){
    val x = createFile(Paths.get("/usr"), "astring")
    val y = createFile(Paths.get("/lib"), "astring")
    val x1 = rm(Paths.get("/usr"))
    val y1 = rm(Paths.get("/lib"))
    assert(seq(seq(x, y), seq(x1, y1)).equivalentTo(
                      seq(seq(x, y), seq(y1, x1))))
  }

  test("program equivalence 9 - Cp"){
    val x = createFile(Paths.get("/usr"), "a")
    val y = cp(Paths.get("/usr"), Paths.get("/lib"))
    val z = createFile(Paths.get("/lib"), "a")
    assert(seq(x, y).equivalentTo(seq(x, z)))
  }

  test("trivial program with non-deterministic output") {
    val g = Graph[Expr, DiEdge](
      ite(testFileState(Paths.get("/foo"), IsDir), mkdir(Paths.get("/bar")), ESkip),
      mkdir(Paths.get("/foo")))

    assert(g.fsGraph.toExecTree(true).isDeterministic == false)
  }

  test("trivial program with non-deterministic error"){
    val g = Graph[Expr, DiEdge](mkdir("/foo"), mkdir("/foo/bar"))
    assert(g.fsGraph.toExecTree(true).isDeterministic == false)
  }

  test("Is a singleton graph deterministic") {
    val t = Graph[Expr, DiEdge](ite(testFileState(Paths.get("/foo"), IsDir), ESkip,
                                            mkdir(Paths.get("/foo")))).fsGraph.toExecTree()
    assert(t.isDeterministic() == true)
    assert(t.isDeterError() == false)
  }

  test("Two-node non-deterministic graph") {
    val g = Graph[Expr, DiEdge](mkdir(Paths.get("/foo")),
      ite(testFileState(Paths.get("/foo"), IsDir), ESkip, mkdir(Paths.get("/bar"))))
    assert(false == g.fsGraph.toExecTree(true).isDeterministic())
  }

  test("a bug") {
    val p = Paths.get("/usr/games/sl")
    val c = ""
    val n1 = createFile(p, c)
    val n2 = ite(testFileState(p, IsFile),
      rm(p) >> createFile(p, c),
      ite(testFileState(p, DoesNotExist), createFile(p, c), ESkip))
    assert(false == Graph[Expr, DiEdge](n1, n2).fsGraph.toExecTree(true).isDeterministic())
  }

  test("should be deterministic") {
    val p = Paths.get("/usr/foo")
    val c = "c"
    val n = createFile(p, c)
    assert(true == Graph[Expr, DiEdge](n, n).fsGraph.toExecTree(true).isDeterministic())
  }

  test("independent rm and createFile") {
    val p = Paths.get("/usr/foo")
    val e1 = rm(p)
    val e2 = createFile(p, "")
    val g = Graph[Expr, DiEdge](e1, e2)
    assert(e1.commutesWith(e2) == false, "commutativity check is buggy")
    assert(false == g.fsGraph.toExecTree(true).isDeterministic())
  }

  test("package with config file non-deterministic graph") {
    val program = """
      file {'/usr/games/sl': ensure => present, content => "something"}
      package {'sl': ensure => present }
                  """
    val g = PuppetParser.parse(program).eval().resourceGraph().fsGraph("ubuntu-trusty")
    assert(false == g.toExecTree(true).isDeterministic())
  }

  test("should be non-deterministic") {
    info("Should work")
    val p = Paths.get("/usr/foo")
    val c1 = "contents 1"
    val c2 = "contents 2"
    val stmt1 = ite(testFileState(p, DoesNotExist), createFile(p, c1), rm(p) >> createFile(p, c1))
    val stmt2 = ite(testFileState(p, DoesNotExist), createFile(p, c2), rm(p) >> createFile(p, c2))
    assert(false == Graph[Expr, DiEdge](stmt1, stmt2).fsGraph.toExecTree(true).isDeterministic)
  }

  test("service") {
    val program = """
      file {'/foo': ensure => directory}
      file {'/foo/bar': ensure => file, before => Service['foo'] }
      service {'foo':}
                  """
    val g = PuppetParser.parse(program).eval().resourceGraph().fsGraph("ubuntu-trusty")
    val exprs = g.exprs.values.toArray
    assert(true == g.toExecTree().isDeterministic())
  }

  test("slicing regression: thias-bind-buggy") {
    val m = PuppetParser.parse(
      """
        package { 'bind': ensure => installed }

        service { 'named':
            require   => Package['bind'],
            hasstatus => true,
            enable    => true,
            ensure    => running,
            restart   => '/sbin/service named reload',
        }

        file { "/var/named":
            require => Package['bind'],
            ensure  => directory,
            owner   => 'root',
            group   => 'named',
            mode    => '0770',
            seltype => 'named_log_t',
        }

          file { "/var/named/example.com":
              content => "lol",
              notify  => Service['named'],
          }
      """).eval().resourceGraph().fsGraph("centos-6")

    assert(m.pruneWrites().toExecTree().isDeterministic() == false,
      "slicing changed the result of determinism")
  }

  test("openssh class from SpikyIRC benchmark") {
    val m = PuppetParser.parse("""
      package {'openssh':
        ensure => latest,
      }

      service {'sshd':
        ensure     => running,
        enable     => true,
      }

      file {'sshd_config':
        ensure => present,
        path   => '/etc/ssh/sshd_config',
        mode   => '0600',
        owner  => 'root',
        group  => 'root',
        source => 'puppet:///modules/ssh/sshd_config',
        notify => Service['sshd'],
      }
    """).eval().resourceGraph().fsGraph("centos-6")

    comprehensiveIsDet(m)
  }

  test("missing dependency between file and directory (autorequire)") {
    val m = PuppetParser.parse("""
      file{"/dir": ensure => directory }

      file{"/dir/file": ensure => present}
      """).eval().resourceGraph().fsGraph("centos-6")

    assert(comprehensiveIsDet(m) == true)
  }

  test("pdurbin-java-jpa reduced") {

    // This test illustrates a subtle issue with the way we model package vs.
    // file resources. We have packages create their entire directory tree, but
    // files do not (and should not). In this example, the vim-enhanced package
    // creates a file in /etc. So, if it runs first, it creates the /etc
    // directory. But, if the file /etc/inittab runs first, it will signal an
    // error if /etc does not exist.

    val m = PuppetParser.parse(
      """
         file {"/etc/inittab":
            content => "my contents"
          }
        package{"vim-enhanced": }
      """).eval.resourceGraph.fsGraph("centos-6")
    assert(comprehensiveIsDet(m))
  }

  test("pruning a sinple node should produce the empty graph") {
    val m = PuppetParser.parse(
      """
        user{"alice": managehome => true}
      """).eval.resourceGraph.fsGraph("ubuntu").pruneWrites()
    assert(m.deps.isEmpty)
  }

  test("packages ircd-hybrid and httpd") {
    // Both packages create files in /var. When we don't force /var to be
    // a directory, this test checks that we do force / to be a directory.
    val m = PuppetParser.parse("""
      package{'ircd-hybrid': }
      package{'httpd': }
      """).eval.resourceGraph.fsGraph("centos-6").pruneWrites.toExecTree(true)

    assert (m.branches == Nil)
  }

  test("two independent packages") {
    val m = PuppetParser.parse(
      """
          $packages_to_install = [
            'unzip',
            'vim-enhanced',
          ]

          package { $packages_to_install:
            ensure => installed,
          }

            """).eval.resourceGraph.fsGraph("centos-6")

    val List(e1, e2) = m.exprs.values.toList
    assert (e1.commutesWith(e2))
    assert(comprehensiveIsDet(m) == true)
  }

  test("java-reduced-less") {
    val m = PuppetParser.parse(
      """
        $packages_to_install = [
          'unzip',
          'ant',
        ]

        package { $packages_to_install:
          ensure => installed,
        }

        file { '/otherb':
          content => "foobar"
        }
      """).eval.resourceGraph.fsGraph("centos-6")
    assert(comprehensiveIsDet(m) == true)
  }

  test("A totally ordered manifest should be completely pruned") {

    val g = PuppetParser.parse(
      """
      file{"/a": ensure => directory }
      file{"/a/b": require => File["/a"] }
      """).eval.resourceGraph.fsGraph("centos-6")

    assert(g.pruneWrites.deps.isEmpty)
  }


  test("Overwriting a file in a package and getting the dependency right") {
    val g = PuppetParser.parse(
      """
          file {"/etc/apache2/sites-available/000-default.conf":
            content => "dummy config", require => Package["apache2"]
          }
          package{"apache2": ensure => present }
      """).eval.resourceGraph.fsGraph("ubuntu-trusty").pruneWrites()
    assert(g.deps.isEmpty)
  }

  test("Pruning is not effective in this case") {
    val m = PuppetParser.parse(
      """
          package{"collectd": }
          package{"collectd-rrdtool": require => Package["collectd"]}
          file { 'collectd_swap':
            ensure  => file,
            path    => '/etc/collectd.d/swap.conf',
            content => "LoadPlugin swap\n",
            owner   => 'root',
            group   => 'root',
            mode    => '0644',
            require => Package['collectd']
          }
      """).eval.resourceGraph().fsGraph("centos-6").pruneWrites

    assert(m.deps.nodes.size >= 3)
  }

  test("dir?(p) != emptydir?(p) is trivial if there is a file in p") {

    val prefix = mkdir("/foo".toPath) >> mkdir("/foo/bar".toPath)
    val e1 = prefix >> ite(testFileState("/foo".toPath, IsEmptyDir), ESkip, EError)
    val e2 = prefix >> ite(testFileState("/foo".toPath, IsDir), ESkip, EError)
    assert(e1.equivalentTo(e2) == false)
  }

  test("dir?(p) != emptydir?(p)") {

    val e1 = ite(testFileState("/foo".toPath, IsEmptyDir), ESkip, EError)
    val e2 = ite(testFileState("/foo".toPath, IsDir), ESkip, EError)
    assert(e1.equivalentTo(e2) == false)
  }

  test("rm can remove empty directories") {
    val foo = "/foo".toPath
    val e = ite(testFileState(foo, IsEmptyDir),
              rm(foo) >> mkdir(foo),
              ESkip)
    assert(e.equivalentTo(ESkip))
  }

  test("force attribute on files") {
    val g1 = PuppetParser.parse(
      """
      file{"/foo/bar":
        force => true
      }
      """).eval.resourceGraph.fsGraph("ubuntu-trusty").expr()

    val g2 = PuppetParser.parse(
      """
        file{"/foo/bar": }
      """).eval.resourceGraph.fsGraph("ubuntu-trusty").expr()
    // This is totally obvious. It is not clear how to write a good test case
    // for this attribute.
    assert(g1.equivalentTo(g2) == false)
  }

  test(" a terrible manifest that uses ssh_authorized_keys and file") {
    // This example is described in the PLDI'16 paper on Rehearsal.
    val g = PuppetParser.parse(
      """
      file {'/home/arjun/.ssh':
        ensure => directory,
      }

      ssh_authorized_key{'arjun@local':
        user => 'arjun',
        type => 'ssh-rsa',
        key  => 'foobar',
        ensure => present,
        require => File['/home/arjun/.ssh']
      }

      file{'/home/arjun/.ssh/authorized_keys':
        content => "ssh-rsa foobar",
        ensure => present,
        require => File['/home/arjun/.ssh']
      }
      """).eval.resourceGraph.fsGraph("ubuntu-trusty")

    assert(g.toExecTree().isDeterministic == false)
  }

  test("auto-require between user and ssh key") {
    val g = PuppetParser.parse(
      """
      ssh_authorized_key{'arjun@local':
        user => 'arjun',
        type => 'ssh-rsa',
        key  => 'foobar',
        ensure => present
      }

      user{'arjun': managehome => true}
      """).eval.resourceGraph.fsGraph("ubuntu-trusty")

    assert(g.toExecTree().isDeterministic() == true)
  }

  test("trivially deterministic: ssh_authorized_key and user") {
    val tree = PuppetParser.parse("""
      user{'arjun': managehome => true}
      ssh_authorized_key{'key1': user => arjun, type => 'ssh-rsa', key => 'x'}
      ssh_authorized_key{'key2': user => arjun, type => 'ssh-rsa', key => 'y'}
      """).eval().resourceGraph().fsGraph("ubuntu-trusty").toExecTree()
    assert(tree.branches.length < 2)
  }


}

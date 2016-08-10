class IdempotenceEvaluationSuite extends org.scalatest.FunSuite
  with org.scalatest.BeforeAndAfterEach {


  import rehearsal._
  import PuppetParser.parseFile

  import FSSyntax._
  import scalax.collection.Graph
  import scalax.collection.GraphEdge.DiEdge
  import rehearsal.Implicits._
  import java.nio.file.Paths
  import SymbolicEvaluator.{predEquals}
  import PuppetSyntax._

  val root = "Private/parser-tests/good"

  override def beforeEach() = {
    FSSyntax.clearCache()
  }

  test("nfisher-SpikyIRC.pp") {
    val g = parseFile(s"$root/nfisher-SpikyIRC.pp").eval.resourceGraph.fsGraph("centos-6")
    assert(g.expr().isIdempotent())
  }

  test("dhoppe-monit.pp") {
    val g = parseFile(s"$root/dhoppe-monit.pp").eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(g.expr().isIdempotent())
  }

  test("dhoppe-monit_BUG.pp") {
    val g = parseFile(s"$root/dhoppe-monit_BUG.pp").eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(g.expr().isIdempotent())
  }

  test("thias-bind.pp") {
    assert(parseFile(s"$root/thias-bind.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("thias-bind.pp pruned") {
    assert(parseFile(s"$root/thias-bind.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("puppet-hosting_deter.pp") {
    assert(parseFile(s"$root/puppet-hosting_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("puppet-hosting_deter.pp pruned") {
    assert(parseFile(s"$root/puppet-hosting_deter.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("antonlindstrom-powerdns_deter.pp") {
    assert(parseFile(s"$root/antonlindstrom-powerdns_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("antonlindstrom-powerdns_deter.pp pruned") {
    assert(parseFile(s"$root/antonlindstrom-powerdns_deter.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("spiky-reduced-deterministic.pp") {
    assert(parseFile(s"$root/spiky-reduced-deterministic.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("spiky-reduced-deterministic.pp pruned") {
    assert(parseFile(s"$root/spiky-reduced-deterministic.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("ghoneycutt-xinetd_deter.pp") {
    assert(parseFile(s"$root/ghoneycutt-xinetd_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("ghoneycutt-xinetd_deter.pp pruned") {
    assert(parseFile(s"$root/ghoneycutt-xinetd_deter.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("mjhas-amavis.pp") {
    assert(parseFile(s"$root/mjhas-amavis.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("mjhas-amavis.pp pruned") {
    assert(parseFile(s"$root/mjhas-amavis.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("mjhas-clamav.pp") {
    assert(parseFile(s"$root/mjhas-clamav.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("mjhas-clamav.pp pruned") {
    assert(parseFile(s"$root/mjhas-clamav.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("Nelmo-logstash_deter.pp") {
    assert(parseFile(s"$root/Nelmo-logstash_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("Nelmo-logstash_deter.pp pruned") {
    assert(parseFile(s"$root/Nelmo-logstash_deter.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("pdurbin-java-jpa-tutorial.pp pruned") {
    assert(parseFile(s"$root/pdurbin-java-jpa-tutorial.pp").eval.resourceGraph
            .fsGraph("centos-6").expr().isIdempotent())
  }

  test("thias-ntp_deter.pp") {
    assert(parseFile(s"$root/thias-ntp_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("thias-ntp_deter.pp pruned") {
    assert(parseFile(s"$root/thias-ntp_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("xdrum-rsyslog_deter.pp") {
    assert(parseFile(s"$root/xdrum-rsyslog_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("xdrum-rsyslog_deter.pp pruned") {
    assert(parseFile(s"$root/xdrum-rsyslog_deter.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("BenoitCattie-puppet-nginx.pp") {
    assert(parseFile(s"$root/BenoitCattie-puppet-nginx.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("BenoitCattie-puppet-nginx.pp pruned") {
    assert(parseFile(s"$root/BenoitCattie-puppet-nginx.pp").eval.resourceGraph
            .fsGraph("ubuntu-trusty").expr().isIdempotent())
  }

  test("ssh_authorized_keys should be idempotent (regression)") {
    val manifest =
      """
      user { 'deployer':
        ensure     => present,
        managehome => true,
      }

      ssh_authorized_key { 'deployer_key':
        ensure  => present,
        key     => 'X',
        user    => 'deployer',
        require => User['deployer'],
      }
      """
    val g = PuppetParser.parse(manifest).eval.resourceGraph().fsGraph("centos-6")
    val e = g.expr()

    assert(g.pruneWrites.toExecTree(true).isDeterministic(), "not deterministic")
    assert(e.isIdempotent() == true, "not idempotent")
  }

  test("pdurbin-java-jpa-tutorial.pp") {
    val g = parseFile(s"$root/pdurbin-java-jpa-tutorial.pp").eval.resourceGraph.fsGraph("centos-6")
    assert(g.pruneWrites.toExecTree(true).isDeterministic())
    assert(g.expr.isIdempotent == true)
  }

  test("small non-idempotent example (in FS)") {
   import FSSyntax._
    val dst = "/dst.txt"
    val src = "/src.txt"
    val e1 = ite(testFileState(dst, IsFile),
      rm(dst) >> cp(src, dst),
      ite(testFileState(dst, DoesNotExist),
        cp(src, dst),
        EError))
    val e2 = ite(testFileState(src, IsFile), rm(src), ESkip)
    val e = e1 >> e2

    assert(e.isIdempotent() == false)
    assert(e.isIdempotent() == false)
  }

  test("small non-idempotent example (in Puppet)"){
    val g = PuppetParser.parseFile(s"$root/non-idempotent.pp").eval.resourceGraph.fsGraph("ubuntu-trusty")
    assert(g.expr().isIdempotent() == false)

  }

}

class DeterminismEvaluationSuite extends FunSuitePlus
  with org.scalatest.BeforeAndAfterEach {

  import rehearsal._
  import PuppetParser.parseFile
  import rehearsal.Implicits._

  val root = "parser-tests/good"

  override def beforeEach() = {
    FSSyntax.clearCache()
  }

  def mytest(filename: String,
             isDeterministic: Boolean,
             os: String = "ubuntu-trusty",
             onlyPrune: Boolean = false): Unit = {
    test(filename) {
      val rg = parseFile(s"$root/$filename").eval.resourceGraph
      val g = rg.fsGraph(os)

      val unprunedResult = if (onlyPrune == false) {
          val (r, t) = time(g.toExecTree(true).isDeterministic)
          info(s"Deterministic without pruning? $r (${t}ms)")
          Some(r)
        }
        else {
          None
      }
      val (prunedResult, t) = time(g.pruneWrites.toExecTree(true)
                                    .isDeterministic)
      info(s"Deterministic with pruning? $prunedResult (${t}ms)")
      unprunedResult match {
        case None => assert(prunedResult == isDeterministic)
        case Some(unpruned) =>
          assert (unpruned == isDeterministic &&
                  prunedResult == unpruned)
      }
    }
  }

  mytest("dhoppe-monit.pp", true)
  mytest("thias-bind.pp", true)

  test("dhoppe-monit_BUG.pp") {
    val g = parseFile(s"$root/dhoppe-monit_BUG.pp").eval.resourceGraph
      .fsGraph("ubuntu-trusty")
    assert(g.toExecTree(true).isDeterError() == true)
  }

  mytest("thias-bind-buggy.pp", false, os = "centos-6")

  test("puppet-hosting.pp") {
    intercept[PackageNotFound] {
      val g = parseFile(s"$root/puppet-hosting.pp").eval.resourceGraph
        .fsGraph("ubuntu-trusty")
      // TODO(arjun): This line shouldn't be necessary, but .fsGraph produces
      // a lazy data structure!
      assert(g.toExecTree(true).isDeterministic() == false)
    }
  }

  mytest("puppet-hosting_deter.pp", true)
  mytest("antonlindstrom-powerdns.pp", false)
  mytest("antonlindstrom-powerdns_deter.pp", true)
  mytest("nfisher-SpikyIRC.pp", false, os = "centos-6", onlyPrune = true)
  mytest("spiky-reduced.pp", false, os = "centos-6", onlyPrune = true)
  mytest("spiky-reduced-deterministic.pp", true, os = "centos-6")
  mytest("ghoneycutt-xinetd.pp", false)
  mytest("ghoneycutt-xinetd_deter.pp", true)
  mytest("mjhas-amavis.pp", true)
  mytest("mjhas-clamav.pp", true)
  mytest("clamav-reduced.pp", true)
  mytest("Nelmo-logstash.pp", false, onlyPrune = true)
  mytest("Nelmo-logstash_deter.pp", true)
  mytest("pdurbin-java-jpa-tutorial.pp", true, os = "centos-6")
  mytest("thias-ntp.pp", false)
  mytest("thias-ntp_deter.pp", true)
  mytest("xdrum-rsyslog.pp", false)
  mytest("xdrum-rsyslog_deter.pp", true)
  mytest("BenoitCattie-puppet-nginx.pp", true)

}

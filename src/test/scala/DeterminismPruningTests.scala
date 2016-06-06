//import java.nio.file.Path
//
//import rehearsal.PuppetSyntax.FSGraph
//
//class DeterminismPruningTests extends org.scalatest.FunSuite {
//  import rehearsal._
//  import Implicits._
//  import FSSyntax._
//  import ResourceModel._
//  import scalax.collection.Graph
//  import scalax.collection.GraphEdge.DiEdge
//  import DeterminismPruning._
//  import scalax.collection.GraphPredef._
//
//
//  val mydir = Directory("/mydir").compile("")
//  val myfile = File("/mydir/myfile", CInline("hi"), false).compile("")
//
//  val mydir2 = Directory("/mydir2").compile("")
//  val myfile2 = File("/mydir2/myfile2", CInline("hi2"), false).compile("")
//
////  test("directory resource ensures directory") {
////    val absSt = absEval(mydir)
////    assert(absSt("/mydir") == ADir)
////    assert(absSt("/") == ARead)
////  }
////
////  test("file resource writes file and reads directory") {
////    val absSt = absEval(myfile)
////    assert(absSt("/mydir") == ARead)
////    assert(absSt("/mydir/myfile") == AWrite)
////    assert(absSt("/") == ARead)
////  }
////
////  test("pruning a lone file resource ") {
////    val g = PuppetParser.parse(
////      """
////        file{"/foo": ensure => present }
////      """).eval().resourceGraph().fsGraph("")
////    val pruned = Slicing.sliceGraph(g)
////    assert(pruned.exprs.values.head.fileSets.writes.isEmpty)
////  }
////
////  test("pruning a file resource that may commute with a directory ") {
////    val absSt = join(absEval(myfile), absEval(mydir))
////    val e_ = pruneExpr(Set("/mydir/myfile"), absSt, Map.empty, myfile)._2
////    assert(e_ != Skip)
////  }
////
////  test("pruning a directory that may commute with a file") {
////    val absSt = join(absEval(myfile), absEval(mydir))
////    val e_ = pruneExpr(Set("/mydir/myfile"), absSt, Map.empty, mydir)._2
////    info(mydir.toString)
////    info(absEval(mydir).toString)
////    assert(e_ != Skip)
////  }
//
//  test("missing dependency between file and directory") {
//    val m = PuppetParser.parse("""
//      file{"/dir": ensure => directory }
//
//      file{"/dir/file": ensure => present}
//                               """).eval().resourceGraph().fsGraph("")
//    val pruned = m.pruneWrites()
//
//    assert(SymbolicEvaluator.isDeterministic(pruned) == false)
//  }
//
//  test("2: missing deps") {
//    val mydir = Directory("/mydir").compile("")
//    val myfile = File("/mydir/myfile", CInline("hi"), false).compile("")
//
//    val g = FSGraph(Map(1 -> mydir, 2 -> myfile),
//      Graph[Int, DiEdge](1, 2))
//    val g_ = g.pruneWrites()
//    assert(pruneablePaths(g) == Set[Path]("/mydir/myfile"))
//  }
//
//  test("Can prune writes to file resources that are not read elsewhere") {
//    val g = FSGraph(Map(1 -> mydir, 2 -> myfile, 3 -> mydir2, 4 -> myfile2),
//                    Graph[Int, DiEdge](1 ~> 2, 3 ~> 4))
//    val ps = pruneablePaths(g)
//    assert(ps.contains("/mydir/myfile"))
//    assert(ps.contains("/mydir2/myfile2"))
//    assert(SymbolicEvaluator.isDeterministic(g.pruneWrites()) == true)
//  }
//
//  test("pruning packages") {
//    val e = Package("vim", true).compile("ubuntu-trusty")
//    val st = absEval(e).get
//    // Remove everything that is definitely a file or a directory. That should
//    // only leave /packages/vim, which must simply exist
//    info(st.toString)
//    assert(st.toList
//           .filterNot({ case (_, x) => x.subsetOf(Set(ADir, AUntouched)) ||
//                                       x.subsetOf(Set(AFile, AUntouched)) })
//           .length == 2)
//  }
//
//  test("a file resource completely determines the state of the file") {
//    val resource = File("/mydir/myfile", CInline("hi"), false)
//    val st = absEval(resource.compile("")).get
//    assert(st("/mydir/myfile") == Set(AFile))
//  }
//
//  test("file resource reduced") {
//    val p = "/mydir/myfile".toPath
//    val c = "hi"
//    val e = ite(testFileState(p, IsFile),
//      rm(p) >> createFile(p, c),
//      ite(testFileState(p, DoesNotExist),
//        createFile(p, c),
//        Error))
//    val st = absEval(e).get
//    assert(st(p) == Set(AFile))
//  }
//
//
//  test("2: create file or error") {
//    val p = "/mydir/myfile".toPath
//    val c = "hi"
//    val e = ite(testFileState(p, DoesNotExist),
//               createFile(p, c),
//               Error)
//    val st = absEval(e).get
//    assert(st("/mydir/myfile") == Set(AFile))
//  }
//
//  test("2: rm(p) >> create(p)") {
//    val p = "/mydir/myfile".toPath
//    val c = "hi"
//    val e = rm(p) >> createFile(p, c)
//    val st = absEval(e).get
//    assert(st == Map("/mydir".toPath -> Set(ADir),
//                     "/mydir/myfile".toPath -> Set(AFile)))
//  }
//
//  test("a conditional write leaves path in an indeterminate state") {
//    val e = ite(testFileState("/a", IsDir), Skip, mkdir("/b"))
//    val st = absEval(e).get
//    info(st.toString)
//    assert(st("/b") == Set(ADir, AUntouched))
//    assert(st("/a") == Set(ADir, AFile, ADoesNotExist))
//  }
//
//  ignore("mkdir(p) >> if(dir(p))..."){
//    val p = "/mydir"
//    val e = mkdir(p) >> ite(testFileState(p, IsDir), Skip, Error)
//    val pruned = ite(testFileState(p, DoesNotExist) && testFileState(p.getParent, IsDir),
//                    Skip, Error) >>
//                 ite(True, Skip, Error)
//    assert(prune(Set(p), e) == pruned)
//
//  }
//
//}

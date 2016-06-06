class CommutativityTests extends org.scalatest.FunSuite {

  import rehearsal._
  import FSSyntax._
  import Implicits._

  // NOTE: It would be brittle to test that the set of directories contains only
  // /parent, since the model assumes that certain paths, such as /
  // and /usr are directories.

  test("idempotent directory creation") {
    val p = ite(testFileState("/parent", IsDir), ESkip, mkdir("/parent"))
    assert(p.fileSets.dirs.contains("/parent"))
  }

  test("Write to idempotent op path invalidates idempotent op") {
    val p = ite(testFileState("/a", DoesNotExist), mkdir("/a"), ESkip) >>
            rm("/a")
    assert(p.fileSets.dirs.contains("/a") == false)
    assert(p.fileSets.writes == Set[Path]("/a"))
  }

  test("non-idempotent ops should not commute") {
    val p = ite(testFileState("/a", IsDir), ESkip, mkdir("/a")) >> rm("/a")
    val q = ite(testFileState("/a", DoesNotExist), mkdir("/a"), ESkip)

    assert(false == p.commutesWith(q))
  }

  test("idempotent ops should commute") {
    val p = ite(testFileState("/a", IsDir), ESkip, mkdir("/a")) >> mkdir("/b")
    val q = mkdir("/c") >> ite(testFileState("/a", IsDir), ESkip, mkdir("/a"))
    println(p.fileSets)
    println(q.fileSets)
    assert(p.commutesWith(q))
  }
}

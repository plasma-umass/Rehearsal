package rehearsal

object ResourceModel {

  import java.nio.file.Paths
  import scala.collection.immutable.Set
  import FSSyntax._
  import rehearsal.Implicits._
  import scalaj.http.Http
  import edu.umass.cs.extras.Implicits._

  sealed trait Content
  case class CInline(src: String) extends Content
  case class CFile(src: String) extends Content

  sealed trait Res {
    def compile(distro: String): Expr = ResourceModel.compile(this, distro)
  }

  case class File(path: Path, content: Content, force: Boolean) extends Res
  case class EnsureFile(path: Path, content: Content) extends Res
  case class AbsentPath(path: Path, force: Boolean) extends Res
  case class Directory(path: Path) extends Res
  case class Package(name: String, present: Boolean) extends Res
  case class Group(name: String, present: Boolean) extends Res
  case class User(name: String, present: Boolean, manageHome: Boolean) extends Res
  case class Service(name: String) extends Res {
    val path = s"/etc/init.d/$name"
  }
  case class Cron(present: Boolean, command: String, user: String, hour: String,
                  minute: String, month: String, monthday: String) extends Res
  case class Host(ensure: Boolean, name: String, ip: String, target: String) extends Res

  case class SshAuthorizedKey(user: String, present: Boolean, name: String, key: String) extends Res {
    val keyPath = s"/home/$user/.ssh/$name"
  }

  case object Notify extends Res

  def queryPackage(distro: String, pkg: String): Option[Set[Path]] = {
    val resp = logTime(s"Fetching package listing for $pkg ($distro)") {
      Http(s"${Settings.packageHost}/query/$distro/$pkg").timeout(2 * 1000, 60 * 1000).asString
    }
    if (resp.isError) {
      None
    }
    else {
      Some(resp.body.lines.map(s => Paths.get(s)).toSet)
    }
  }

  def compile(r: Res, distro: String): Expr = r match {
    case EnsureFile(p, CInline(c)) =>
      ite(testFileState(p, IsFile), rm(p), ESkip) >> createFile(p, c)
    case EnsureFile(p, CFile(s)) =>
      ite(testFileState(p, IsFile), rm(p), ESkip) >> cp(s, p)
    case File(p, CInline(c), false) =>
      ite(testFileState(p, IsFile),
         rm(p) >> createFile(p, c),
         ite(testFileState(p, DoesNotExist),
            createFile(p, c),
            EError))
    case File(p, CInline(c), true) => {
      val paths = {
        def loop(path: Path): List[Path] = {
          if (path.getParent == null) {
            Nil
          }
          else {
            path :: loop(path.getParent)
          }
        }
        loop(p).reverse
      }
      val mkdirs = paths.map(p =>
        ite(testFileState(p, IsDir), ESkip, rm(p) >> mkdir(p)))
      ESeq(mkdirs: _*) >>
      ite(testFileState(p, IsDir) || testFileState(p, IsFile), rm(p), ESkip) >>
      createFile(p, c)
    }
    case File(p, CFile(s), false) =>
      ite(testFileState(p, IsFile),
        rm(p) >> cp(s, p),
        ite(testFileState(p, DoesNotExist),
          cp(s, p),
          EError))
    case File(p, CFile(s), true) =>
      ite(testFileState(p, IsDir) || testFileState(p, IsFile),
        rm(p), ESkip) >>
      cp(s, p)
    case AbsentPath(p, false) =>
      // TODO(arjun): why doesn't this work for directories too?
      ite(testFileState(p, IsFile), rm(p), ESkip)
    case AbsentPath(p, true) =>
      // TODO(arjun): Can simplify the program below
      ite(testFileState(p, IsDir),
          rm(p), // TODO(arjun): need to implement directory removal in fsmodel
          ite(testFileState(p, IsFile),
             rm(p),
             ESkip))
    case Directory(p) =>
      ite(testFileState(p, IsDir),
         ESkip,
         ite(testFileState(p, IsFile),
            rm(p) >> mkdir(p),
            mkdir(p)))
    case User(name, present, manageHome) => {
      val u = Paths.get(s"/etc/users/$name")
      val g = Paths.get(s"/etc/groups/$name")
      val h = Paths.get(s"/home/$name")
      present match {
        case true => {
          val homeCmd = if (manageHome) {
            ite(testFileState(h, DoesNotExist), mkdir(h), ESkip)
          }
          else {
            ESkip
          }
          ite(testFileState(u, DoesNotExist), mkdir(u), ESkip) >>
          ite(testFileState(g, DoesNotExist), mkdir(g), ESkip) >>
          homeCmd
        }
        case false => {
          val homeCmd = if (manageHome) {
            ite(testFileState(h, DoesNotExist), ESkip, rm(h))
          }
          else {
            ESkip
          }
          ite(testFileState(u, DoesNotExist), ESkip, rm(u)) >>
          ite(testFileState(g, DoesNotExist), ESkip, rm(g)) >>
          homeCmd
        }
      }
    }
    case Group(name, present) => {
      val p = s"/etc/groups/$name"
      present match {
        case true => ite(testFileState(p, DoesNotExist), mkdir(p), ESkip)
        case false => ite(!testFileState(p, DoesNotExist), rm(p), ESkip)
      }
    }
    case Package(name, true) => {

      val paths = queryPackage(distro, name).getOrElse(throw PackageNotFound(distro, name))
      // HACK: Why doesn't the .ancestors implicit method work?
      val dirs = paths.map(p => new edu.umass.cs.extras.Implicits.RichPath(p).ancestors().toSet).reduce(_ union _) - root -- Settings.assumedDirs.toSet
      val files = paths -- dirs

      val mkdirs = dirs.toSeq.sortBy(_.getNameCount)
        .map(d => ite(testFileState(d, IsDir), ESkip, mkdir(d)))

      val somecontent = ""
      val createfiles = files.toSeq.map((f) => createFile(f, somecontent))
      val exprs = mkdirs ++ createfiles


      ite(testFileState(s"/packages/${name}", IsFile),
          ESkip,
          createFile(s"/packages/${name}", "") >> ESeq(exprs: _*))
    }
    case Package(name, false) => {
      // TODO(arjun): Shouldn't this only remove files and newly created directories?
      val files =  queryPackage(distro, name).getOrElse(throw PackageNotFound(distro, name)).toList
      val exprs = files.map(f => ite(testFileState(f, DoesNotExist), ESkip, rm(f)))
      val pkgInstallInfoPath = s"/packages/$name"
      // Append at end
      ite(testFileState(pkgInstallInfoPath, DoesNotExist),
          ESkip,
          ESeq((rm(pkgInstallInfoPath) :: exprs) :_*))
    }
    case self@SshAuthorizedKey(user, present, _, key) => {
      val p = self.keyPath
      present match {
        case true => {
          val sshDir = s"/home/$user/.ssh".toPath
          val dummyPath = s"/home/$user/.ssh/authorized_keys".toPath
          ite(testFileState(sshDir, IsDir), ESkip, mkdir(sshDir)) >>
            ite(testFileState(p, IsFile), rm(p), ESkip) >> createFile(p, key) >>
            ite(testFileState(dummyPath, IsFile), rm(dummyPath), ESkip) >>
            createFile(dummyPath, "dummy")
        }
        case false => {
          ite(testFileState(p, IsFile), rm(p), ESkip)
        }
      }
    }
    case self@Service(name) => ite(testFileState(self.path, IsFile), ESkip, EError)
    case Cron(present, command, user, hour, minute, month, monthday) => {
      val name = command.hashCode.toString + "-" + command.toLowerCase.filter(ch => ch >= 'a' && ch <= 'z')
      val path = s"${Settings.modelRoot}/crontab-$name"
      present match {
        case false => ite(testFileState(path, DoesNotExist), ESkip, rm(path))
        case true => ite(testFileState(path, DoesNotExist), createFile(path, ""), ESkip)
      }
    }
    case Host(ensure, name, ip, target) => {
      val path = s"${Settings.modelRoot}/host-${name}"
      val e2 = ensure match {
        case false => ite(testFileState(path, DoesNotExist), ESkip, rm(path))
        case true => ite(testFileState(path, DoesNotExist), createFile(path, ip), ESkip)
      }
      val e1 = ite(testFileState(target, DoesNotExist), ESkip, rm(target)) >>
        createFile(target, "managed by Rehearsal")
      e1 >> e2
    }
    case Notify => ESkip
    case _ => ???
  }

}

package rehearsal

object ResourceSemantics {

  import Implicits._
  import PuppetSyntax._
  import ResourceModel._

  case class FSCompileError(msg: String) extends RuntimeException(msg)

  // It is easy to forget to support an optional attribute. For example,
  // file resources can optionally take mode-attribute (to set permissions)
  // that is easy to overlook. Instead of accessing the attribute map directly,
  // we use the consume methods of this class to mark attributes read.
  // Before returning a resource, we use the assertAllUsed to ensure that
  // all attributes have been read.
  //
  // For convenience, the consume methods don't return raw attribute values.
  // Instead, they use extracts (defined in Implicits.scala) to map attribute
  // values to Scala values. For example, the Boolean extractor maps
  // the attribute-strings "yes" and "no" to true and false in Scala.
  class AttrExtractor(attrs: Map[String, Expr]) {

    import scala.collection.mutable.{Map => MutableMap}

    private val state = MutableMap[String, Expr](attrs.toSeq: _*)

    def consume[T](key: String, default: => T)(implicit extractor: Extractor[Expr, T]): T = {
      state.get(key) match {
        case Some(EUndef) => {
          state -= key
          default
        }
        case Some(v) => {
          val r = v.value[T].getOrElse(throw FSCompileError(s"unexpected value for $key, got $v"))
          state -= key
          r
        }
        case None => default
      }
    }

    def consume[T](key: String)(implicit extractor: Extractor[Expr, T]): T = {
      consume[T](key, throw FSCompileError(s"key not found: $key"))
    }

    def unused() = state.toMap

    def assertAllUsed() = {
      if (state.isEmpty == false) {
        throw FSCompileError(s"unused attributes: $state")
      }
    }
  }

  def compile(resource: ResourceVal): Res = {
    val attrs = new AttrExtractor(resource.attrs)
    val result = resource.typ match {
      case "file" => {
        val path = attrs.consume("path", resource.title)
        val content = attrs.consume("content", "")
        val force = attrs.consume("force", false)
        val source = attrs.consume("source", "") // TODO(arjun): I think this is meant to be mutually exclusive with "content"

        attrs.consume("seltype", "")
        attrs.consume("group", "root")
        attrs.consume("owner", "root")
        attrs.consume("mode", "0644")
        attrs.consume("recurse", false) // TODO(arjun): necessary to model?
        attrs.consume("purge", false) // TODO(arjun): necessary to model?

        (attrs.consume("ensure", "present"), source) match {
          case ("present", "") => File(path, CInline(content), force)
          case ("present", s) => File(path, CFile(s), force)
          case ("absent", _) => AbsentPath(path, force)
          case ("file", "") => EnsureFile(path, CInline(content))
          case ("file", s) => EnsureFile(path, CFile(s))
          case ("directory", _) => Directory(path)
          case ("link", _) => File(attrs.consume[String]("target"), CInline(""), true) // TODO(arjun): contents?
          case _ => throw FSCompileError("unexpected ensure value")
        }
      }
      case "package" => {
        val name = attrs.consume("name", resource.title)
        val present = attrs.consume("ensure", "present") match {
          case "present" | "installed" | "latest" => true
          case "absent" | "purged" => false
          case x => throw FSCompileError(s"inexpected ensure for package: $x")
        }
        attrs.consume("source", "")
        attrs.consume("provider", "")
        Package(name, present)
      }
      case "user" => {
        val name = attrs.consume("name", resource.title)
        val present = attrs.consume("ensure", "present") match {
          case "present" => true
          case "absent" => false
          case x => throw FSCompileError(s"unexpected ensure value: $x")
        }
        val manageHome = attrs.consume("managehome", false)
        attrs.consume("comment", "")
        attrs.consume("shell", "")
        attrs.consume("groups", "")
        User(name, present, manageHome)
      }
      case "service" => {
        val name = attrs.consume("name", resource.title)
        attrs.consume("hasstatus", false)
        attrs.consume("hasrestart", false)
        attrs.consume("enable", false)
        attrs.consume("ensure", "running")
        attrs.consume("restart", "")
        Service(name)
      }
      case "ssh_authorized_key" => {
        val user = attrs.consume[String]("user")
        val present = attrs.consume[String]("ensure", "present") match {
          case "present" => true
          case "absent" => false
          case x => throw FSCompileError(s"unexpected ensure value: $x")
        }
        val key = attrs.consume[String]("key")
        val name = attrs.consume("name", resource.title)
        attrs.consume("type", "rsa") // TODO(arjun): What is the actual default type?
        SshAuthorizedKey(user, present, name, key)
      }
      case "notify" => {
        attrs.consume("name", "")
        attrs.consume("message", "")
        attrs.consume("withpath", "")
        Notify
      }
      case "cron" => {
        val present = attrs.consume[String]("ensure", "present") match {
          case "present" => true
          case "absent" => false
          case x => throw FSCompileError(s"unexpected ensure value: $x")
        }
        val command = attrs.consume[String]("command")
        val user = attrs.consume[String]("user", "root")
        val hour = attrs.consume[String]("hour", "*")
        val minute = attrs.consume[String]("minute", "*")
        val month = attrs.consume[String]("month", "*")
        val monthday = attrs.consume[String]("monthday", "*")
        Cron(present, command, user, hour, minute, month, monthday)
      }
      case "host" => {
        val ensure = attrs.consume[String]("ensure", "present") match {
          case "present" => true
          case "absent" => false
          case x => throw FSCompileError(s"unexpected ensure value: $x")
        }
        attrs.consume[String]("comment", "")
        attrs.consume[String]("host_aliases", "")
        attrs.consume[String]("provider", "parsed")
        // TODO(arjun): Probably shouldn't be hardcoded, if we want to support
        // Windows, etc.
        val target = attrs.consume[String]("target", "/etc/hosts")
        val name = attrs.consume("name", resource.title)
        val ip = attrs.consume[String]("ip")
        Host(ensure, name, ip, target)
      }
      case _ => ???
    }
    attrs.assertAllUsed()
    result
  }

}

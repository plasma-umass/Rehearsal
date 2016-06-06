object Main extends App {

  import akka.io.IO
  import akka.actor.{ActorSystem, Props}
  import spray.can.Http

  implicit val system = ActorSystem()

  val server = system.actorOf(Props(classOf[ServerActor]), "server")
  val ioHttp = IO(Http)
  ioHttp ! Http.Bind(server, interface = "0.0.0.0", port = 8080)
}

object PackageCache extends com.typesafe.scalalogging.LazyLogging {

 import scalikejdbc._
 import scala.sys.process.{Process, ProcessLogger}

  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql://127.0.0.1:5432/postgres",
                           "postgres", "")

  DB.localTx { implicit session =>
    assert(sql"""CREATE TABLE IF NOT EXISTS
                 files (distro varchar(100) NOT NULL,
                        package varchar(100) NOT NULL,
                        path varchar(400) NOT NULL)
                        """
           .execute.apply() == false)
  }

  val distroPrefix = "pkglist-"

  val distros: Set[String] =
    Process("docker images")
    .lineStream
    .map(line => line.takeWhile(ch => ch != ' '))
    .filter(line => line.startsWith(distroPrefix))
    .map(distro => distro.drop(distroPrefix.length))
    .toSet

  def queryDB(distro: String, pkg: String): Option[List[String]] = {
    val files = DB.localTx { implicit session =>
    sql"""SELECT path FROM files WHERE distro=$distro AND package=$pkg"""
      .map(_.string("path")).list.apply()
    }

    if (files.isEmpty) {
      None
    }
    else {
      logger.info(s"$distro/$pkg found in database")
      Some(files)
    }
  }

  def queryVM(distro: String, pkg: String): Option[List[String]] = {
    if (!distros.contains(distro)) {
      None
    }

    var files = List[String]()
    val pl = ProcessLogger(file => files = file :: files,
                           err => println(err))
    val exitCode = Process(s"docker run --rm $distroPrefix$distro $pkg") ! pl
    if (exitCode == 0 && !files.isEmpty) {
      Some(files)
    }
    else {
      logger.info(s"$distro/$pkg is not a package")
      None
    }
  }

  def saveToDB(distro: String, pkg: String, files: List[String]): Unit = {
    DB.localTx { implicit session =>
      for (file <- files) {
        assert(sql"INSERT INTO files VALUES ($distro, $pkg, $file)"
               .execute.apply() == false)
      }
    }
  }

  def query(distro: String, pkg: String): Option[List[String]] = {
    queryDB(distro, pkg) orElse {
      queryVM(distro, pkg) map { files =>
        saveToDB(distro, pkg, files)
        files
      }
    }
  }

}

class ServerActor extends spray.routing.HttpServiceActor
  with akka.actor.ActorLogging {

  import akka.util.Timeout
  import spray.can.Http
  import spray.routing._
  import spray.http._
  import scala.concurrent.duration._

  // Long timeout because certain repository queries can take time
  implicit val timeout = Timeout(60.second)

  import context.dispatcher
  import spray.http.Uri.Path.`/`

  def showRequest(req: HttpRequest): String = {
    req.header[HttpHeaders.`Remote-Address`] match {
      case Some(HttpHeaders.`Remote-Address`(src)) =>
        s"$src ${req.method} ${req.uri}"
      case None => s"NO SOURCE ${req.method} ${req.uri}"
    }
  }

  def receive = runRoute {
    logRequest(showRequest _) {
      path("query" / Segment  / Segment) { (distro: String, pkg: String) =>
        PackageCache.query(distro, pkg) match {
          case None => complete(StatusCodes.NotFound)
          case Some(lines) => complete(lines.mkString("\n"))
        }
      }
    }
  }

}
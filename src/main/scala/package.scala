
package object rehearsal {

  import java.nio.file.{Paths, Files}
  import rehearsal.Implicits._
  import org.slf4j.LoggerFactory

  type Path = java.nio.file.Path

  val root = Paths.get("/")

  val logger = com.typesafe.scalalogging.Logger(LoggerFactory.getLogger("rehearsal"))

  def logTime[A](label: String)(body: => A): A = {
    logger.info(s"Starting $label")
    val (result, elapsed) = time(body)
    logger.info(s"$label took $elapsed milliseconds")
    result
  }

  def time[A](thunk: => A): (A, Long) = {
    val start = System.currentTimeMillis
    val r = thunk
    val duration = System.currentTimeMillis - start
    r -> duration
  }

}

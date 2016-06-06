package rehearsal

// Ill-formed input
case class Unexpected(message: String) extends RuntimeException(message)

case class ParseError(msg: String) extends RuntimeException(msg)

case class EvalError(msg: String) extends RuntimeException(msg)

case class PackageNotFound(distro: String, pkg: String) extends RuntimeException(s"$pkg not found for $distro")

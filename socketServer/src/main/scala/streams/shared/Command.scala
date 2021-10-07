package streams.shared

object Command {
  // For now, assume commands have the format: "123 CommandString..."
  def parse(cmd: String): (Int, String) = {
    val ar = cmd.split(" ", 2)
    (ar.head.toInt, ar(1))
  }
}

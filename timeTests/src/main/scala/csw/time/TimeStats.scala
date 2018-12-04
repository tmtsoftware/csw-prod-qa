package csw.time

object TimeStats extends App {

  if (args.length != 1) {
    println("Expected one arg, which is the expected difference between two times in ms")
    System.exit(1)
  }
  val expectedDelay = args.head.toInt
  val times = io.Source.stdin.getLines.toList.map(_.toLong).drop(1000)
  var t0 = times.head
  var errorSum, maxError = 0.0
  var minError = 1000000.0
  times.tail.foreach { t1 =>
    val ms = (t1 - t0) / 1000000.0
    t0 = t1
    val error = math.abs(ms - expectedDelay)
//    println(f"$error%3.4f")
    minError = math.min(minError, error)
    maxError = math.max(maxError, error)
    errorSum = errorSum + error
  }
  val avgError = errorSum / times.size
  println(f"Errors in ms: min: $minError%3.4f, max: $maxError%3.4f, avg: $avgError%3.4f")

}

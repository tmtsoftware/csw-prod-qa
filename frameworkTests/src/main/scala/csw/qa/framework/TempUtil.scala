package csw.qa.framework

import java.io.FileWriter
import java.nio.file.{Files, Path}

// XXX Temporary utility until ContainerCmd supports resource files
// See DEOPSCSW-171: Starting component from command line.
object TempUtil {
  def createStandaloneTmpFile(resourceFileName: String): Path = {
    val hcdConfiguration       = scala.io.Source.fromResource(resourceFileName).mkString
    val standaloneConfFilePath = Files.createTempFile("csw-temp-resource", ".conf")
    val fileWriter             = new FileWriter(standaloneConfFilePath.toFile, true)
    fileWriter.write(hcdConfiguration)
    fileWriter.close()
    standaloneConfFilePath
  }

}

package csw.qa.location

import csw.qa.location.TestAkkaServiceApp.{locationService, system}
import csw.qa.location.TestServiceClientApp.{locationService, system}
import csw.services.location.commons.{ClusterSettings, CswCluster}
import csw.services.location.scaladsl.LocationServiceFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Starts the location service as a standalone app
  */
object LocationServiceApp extends App {
  val cswCluster = CswCluster.withSettings(ClusterSettings().asSeed)
  private val locationService = LocationServiceFactory.withCluster(cswCluster)
  val system = cswCluster.actorSystem

  sys.addShutdownHook(shutdown())

  def shutdown(): Unit = {
    val timeout = 5.seconds
    Await.ready(locationService.shutdown(), timeout)
    Await.ready(system.terminate(), timeout)
  }
}

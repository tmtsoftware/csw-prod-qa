package csw.qa.location

// XXX This is no longer needed, since csw-prod provides the csw-cluster-seed app now

import csw.services.location.commons.ClusterSettings
import csw.services.location.scaladsl.LocationServiceFactory

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Starts the location service as a standalone app
  */
object LocationServiceApp extends App {
  private val locationService = LocationServiceFactory.withSettings(ClusterSettings().onPort(3552).joinLocal(3552))


  sys.addShutdownHook(shutdown())

  def shutdown(): Unit = {
    val timeout = 5.seconds
    Await.ready(locationService.shutdown(), timeout)
  }
}

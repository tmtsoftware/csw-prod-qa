package csw.qa.framework

import java.net.InetAddress
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.ComponentId
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType
import csw.params.core.models.ObsId
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection DuplicatedCode,SameParameterValue
// A client to test locating and communicating with the Test assembly
object TestAssemblyClient3 extends App {

  private val host                                    = InetAddress.getLocalHost.getHostName
  val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "TestAssemblyClientSystem")
  import typedSystem.executionContext

  LoggingSystemFactory.start("TestAssemblyClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  val locationService           = HttpLocationServiceFactory.makeLocalClient(typedSystem)
  implicit val timeout: Timeout = Timeout(10.seconds)

  private val obsId      = ObsId("2020A-001-123")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey  = KeyType.StringKey.make("filter")
  private val prefix     = Prefix("WFOS.blue.filter")
  private val command    = CommandName("myCommand")

  private def makeSetup(encoder: Int, filter: String): Setup = {
    val i1 = encoderKey.set(encoder)
    val i2 = filterKey.set(filter)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)
  }

  val connection = AkkaConnection(ComponentId(Prefix(CSW, "testassembly"), Assembly))
  Await.result(locationService.resolve(connection, timeout.duration), timeout.duration) match {
    case None =>
      log.error(s"Assembly connection not found: $connection")
    case Some(loc) =>
      val assembly = CommandServiceFactory.make(loc)(typedSystem)
      assembly.submitAndWait(makeSetup(0, s"filter0")).onComplete {
        case Success(response) =>
          log.info(s"Single Submit Test Passed: Responses = $response")
          typedSystem.terminate()
        case Failure(ex)       =>
          log.info(s"Single Submit Test Failed: $ex")
          typedSystem.terminate()
      }
  }
}

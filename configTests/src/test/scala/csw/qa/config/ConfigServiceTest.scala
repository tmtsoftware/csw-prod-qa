package csw.qa.config

import java.io.File
import java.net.InetAddress
import java.time.Instant

import akka.actor.ActorSystem
import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.{ConfigClientService, ConfigService}
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.scaladsl.LocationServiceFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import TestFutureExtension.RichFuture
import akka.stream.ActorMaterializer
import csw.services.location.commons.ActorSystemFactory
import csw.services.logging.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}

/**
  * Some tests for the config service.
  *
  * Note: This test assumes that the location and config services are running and that the necessary
  * csw cluster environment variables or system properties are defined.
  */
class ConfigServiceTest extends FunSuite with BeforeAndAfterAll{
  private val log = GenericLoggerFactory.getLogger
  private val path1 = new File(s"some/test1/TestConfig1").toPath
  private val path2 = new File(s"some/test2/TestConfig2").toPath

  private val contents1 = "Contents of some file...\n"
  private val contents2 = "New contents of some file...\n"
  private val contents3 = "Even newer contents of some file...\n"

  private val comment1 = "create comment"
  private val comment2 = "update 1 comment"
  private val comment3 = "update 2 comment"

  implicit val actorSystem: ActorSystem = ActorSystemFactory.remote
  private val locationService = LocationServiceFactory.make()
  private val host = InetAddress.getLocalHost.getHostName

  LoggingSystemFactory.start("ConfigServiceTest", "0.1", host, actorSystem)

  private val clientLocationService = LocationServiceFactory.make()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val configService: ConfigService = ConfigClientFactory.adminApi(actorSystem, clientLocationService)

  override def afterAll() {
    clientLocationService.shutdown().await
    locationService.shutdown().await
  }

  test("Run Tests") {
    runTests(configService, annex = false)
//    runTests(configService, annex = true)
  }

  // Run tests using the given config cs instance
  def runTests(cs: ConfigService, annex: Boolean): Unit = {
    val csClient: ConfigClientService = cs
    log.info(s"Running tests with annex = $annex")

    if (cs.exists(path1).await) cs.delete(path1, "some comment").await
    if (cs.exists(path2).await) cs.delete(path2, "another comment").await
    // Add, then update the file twice
    val date1 = Instant.now
    Thread.sleep(100)
    val createId1 = cs.create(path1, ConfigData.fromString(contents1), annex, comment1).await
    val createId2 = cs.create(path2, ConfigData.fromString(contents1), annex, comment1).await
    val date1a = Instant.now
    Thread.sleep(100) // make sure date is different
    val updateId1 = cs.update(path1, ConfigData.fromString(contents2), comment2).await
    val date2 = Instant.now
    Thread.sleep(100) // make sure date is different
    val updateId2 = cs.update(path1, ConfigData.fromString(contents3), comment3).await
    val date3 = Instant.now

    // Check that we can access each version
    assert(cs.getLatest(path1).await.get.toStringF.await == contents3)
    assert(csClient.getActive(path1).await.get.toStringF.await == contents1)
    assert(cs.getActiveVersion(path1).await.contains(createId1))
    assert(cs.getById(path1, createId1).await.get.toStringF.await == contents1)
    assert(cs.getById(path1, updateId1).await.get.toStringF.await == contents2)
    assert(cs.getById(path1, updateId2).await.get.toStringF.await == contents3)
    assert(cs.getLatest(path2).await.map(_.toStringF.await).get.toString == contents1)
    assert(cs.getById(path2, createId2).await.get.toStringF.await == contents1)

    assert(cs.getByTime(path1, date1).await.get.toStringF.await == contents1)
    assert(cs.getByTime(path1, date1a).await.get.toStringF.await == contents1)
    assert(cs.getByTime(path1, date2).await.get.toStringF.await == contents2)
    assert(cs.getByTime(path1, date3).await.get.toStringF.await == contents3)

    val historyList1 = cs.history(path1).await
    assert(historyList1.size == 3)
    assert(historyList1.head.comment == comment3)
    assert(historyList1(1).comment == comment2)

    val historyList2 = cs.history(path2).await
    assert(historyList2.size == 1)
    assert(historyList2.head.comment == comment1)
    assert(historyList1(2).comment == comment1)

    // Test Active file features
    assert(csClient.getActive(path1).await.get.toStringF.await == contents1)

    cs.setActiveVersion(path1, updateId1, "Setting active version").await
    assert(csClient.getActive(path1).await.get.toStringF.await == contents2)
    assert(cs.getActiveVersion(path1).await.contains(updateId1))

    cs.resetActiveVersion(path1, "Resetting active version").await
    assert(csClient.getActive(path1).await.get.toStringF.await == contents3)
    assert(cs.getActiveVersion(path1).await.contains(updateId2))

    cs.setActiveVersion(path1, updateId2, "Setting active version").await

    // test list()
    val list = cs.list().await
    list.foreach(i => println(i))

    // Test delete
    assert(cs.exists(path1).await)
    cs.delete(path1, "test delete").await
    assert(!cs.exists(path1).await)

    // XXX TODO FIXME: The code below hangs...
    assert(cs.getActive(path1).await.isEmpty)
    assert(cs.getLatest(path1).await.isEmpty)
    assert(cs.getById(path1, createId1).await.get.toStringF.await == contents1)
    assert(cs.getById(path1, updateId1).await.get.toStringF.await == contents2)
  }

}


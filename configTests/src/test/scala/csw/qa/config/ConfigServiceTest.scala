package csw.qa.config

import java.io.File
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.ConfigService
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.scaladsl.{ActorSystemFactory, LocationServiceFactory}
import org.scalatest.FunSuite

import scala.concurrent.{Await, Future}
import scala.util.Try
import scala.concurrent.duration._
import TestFutureExtension.RichFuture
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

/**
  * Some tests for the config service.
  *
  * Note: This test assumes that the location and config services are running and that the necessary
  * csw cluster environment variables or system properties are defined.
  * For example, on my Linux box that is:
  *    -DinterfaceName=enp0s31f6 -DclusterSeeds=192.168.178.66:7777
  */
class ConfigServiceTest extends FunSuite with LazyLogging {
  private val path1 = new File(s"some/test1/TestConfig1").toPath
  private val path2 = new File(s"some/test2/TestConfig2").toPath

  private val contents1 = "Contents of some file...\n"
  private val contents2 = "New contents of some file...\n"
  private val contents3 = "Even newer contents of some file...\n"

  private val comment1 = "create comment"
  private val comment2 = "update 1 comment"
  private val comment3 = "update 2 comment"

  private val clientLocationService = LocationServiceFactory.make()
  implicit val actorSystem: ActorSystem = ActorSystemFactory.remote
  import actorSystem.dispatcher
  implicit val mat = ActorMaterializer()
  private val configService: ConfigService = ConfigClientFactory.adminApi(actorSystem, clientLocationService)

  runTests(configService, annex = false)
  runTests(configService, annex = true)

  // Run tests using the given config cs instance
  def runTests(cs: ConfigService, annex: Boolean): Unit = {
    logger.info(s"Running tests with annex = $annex")

    if (cs.exists(path1).await) cs.delete(path1).await
    if (cs.exists(path2).await) cs.delete(path2).await
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
    assert(cs.getActive(path1).await.get.toStringF.await == comment1)
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
    assert(cs.getActive(path1).await.get.toStringF.await == contents1)

    cs.setActiveVersion(path1, updateId1).await
    assert(cs.getActive(path1).await.get.toStringF.await == contents2)

    cs.resetActiveVersion(path1).await
    assert(cs.getActive(path1).await.get.toStringF.await == contents3)

    cs.setActiveVersion(path1, updateId2).await

    // test list()
    val list = cs.list().await
//    assert(list.size == 2)
//    for (info <- list) {
//      info.path match {
//        case this.path1 => assert(info.comment == this.comment3)
//        case this.path2 => assert(info.comment == this.comment1)
//        case _          =>
//      }
//    }

    // Test delete
    //    cs.delete(path1)
    //    assert(cs.get(path1).isEmpty)
    // XXX TODO: Fix getting previous versions of deleted file using the svn implementation
    //    assert(cs.get(path1, Some(createId1)).get.toString == contents1)
    //    assert(cs.get(path1, Some(updateId1)).get.toString == contents2)
    //    assert(cs.getActive(path1).get.toString == contents3)
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(cs: ConfigService, annex: Boolean): Unit = {

    // Check that we can access each version
    assert(cs.getLatest(path1).await.get.toStringF.await == contents3)
    assert(cs.getLatest(path2).await.get.toStringF.await == contents1)

    // test history()
    val historyList1 = cs.history(path1).await
    assert(historyList1.size == 3)
    assert(historyList1.head.comment == comment3)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment1)

    val historyList2 = cs.history(path2).await
    assert(historyList2.size == 1)
    assert(historyList2.head.comment == comment1)

    // test list()
    val list = cs.list().await
    for (info <- list) {
      info.path match {
        case this.path1 => assert(info.comment == this.comment3)
        case this.path2 => assert(info.comment == this.comment1)
        case _          => // other files: README, *.Active...
      }
    }

    // Should throw exception if we try to create a file that already exists
    assert(Try(cs.create(path1, ConfigData.fromString(contents2), annex, comment2).await).isFailure)
  }

//  // Does some updates and gets
//  private def test3(cs: ConfigService): Unit = {
//    cs.getLatest(path1).await
//    cs.update(path1, ConfigData.fromString(s"${contents2}Added by ${cs.name}\n"), s"$comment1 - ${cs.name}").await
//    cs.getLatest(path2).await
//    cs.update(path2, ConfigData.fromString(s"${contents1}Added by ${cs.name}\n"), s"$comment2 - ${cs.name}").await
//  }
//
//  // Tests concurrent access to a central repository (see if there are any conflicts, etc.)
//  def concurrentTest(managers: List[ConfigService], annex: Boolean): Future[Unit] = {
//    val result = Future.sequence {
//      val f = for (cs <- managers) yield {
//        Future(test3(cs))
//      }
//      // wait here, since we want to do the updates sequentially for each configManager
//      f.foreach(Await.ready(_, 10.seconds))
//      f
//    }
//    result.map(_ => ())
//  }
}


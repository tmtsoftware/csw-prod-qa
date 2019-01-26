package csw.qa.database

import java.net.InetAddress

import akka.actor.typed.javadsl.Adapter
import akka.stream.ActorMaterializer
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import org.jooq.DSLContext

import scala.async.Async.{async, await}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object DatabaseTest extends App {
  private val host = InetAddress.getLocalHost.getHostName
  implicit val system: akka.actor.ActorSystem = ActorSystemFactory.remote

  import system._

  private val typedSystem = Adapter.toTyped(system)
  LoggingSystemFactory.start("DatabaseTest", "0.1", host, system)
  private val log = GenericLoggerFactory.getLogger
  implicit val mat: ActorMaterializer = ActorMaterializer()
  val locationService = HttpLocationServiceFactory.makeLocalClient(system, mat)
  val dbName = "postgres"

  runTest()

  private def runTest(): Unit = {
    val dsl = Await.result(initDatabase(), 10.seconds)

    val resultSet: Seq[(Int, String)] =
      Await.result(
        dsl
          .resultQuery("SELECT * FROM films")
          .fetchAsyncScala[(Int, String)],
        3.seconds)

    log.info(s"resultSet = $resultSet")
    assert(resultSet.size == 2)
    system.terminate()
  }

  private def initDatabase(): Future[DSLContext] = async {
    val dbFactory = new DatabaseServiceFactory(typedSystem)
    val dsl = await(dbFactory.makeDsl(locationService, dbName))
    // ensure database isn't already present
    val getDatabaseQuery = dsl.resultQuery(
      "SELECT datname FROM pg_database WHERE datistemplate = false")
    val resultSet = await(getDatabaseQuery.fetchAsyncScala[String])
    log.info(s"XXX resultSet = $resultSet")

    if (resultSet contains "box_office") {
      log.info("Dropping database: box_office")
      // drop box_office database
      await(dsl.query("DROP DATABASE box_office").executeAsyncScala())
    }

    // create box_office database
    await(dsl.query("CREATE DATABASE box_office").executeAsyncScala())

    val dsl2 = await(dbFactory.makeDsl(locationService, "box_office"))
    log.info("Creating table: films")
    await(
      dsl2
        .query("CREATE TABLE films (id SERIAL PRIMARY KEY, name VARCHAR (10) UNIQUE NOT NULL)")
        .executeAsyncScala())

    log.info("Inserting data in table: films")
    await(
      dsl2
        .query("INSERT INTO films(name) VALUES (?)", "Movie 1")
        .executeAsyncScala())
    await(
      dsl2
        .query("INSERT INTO films(name) VALUES (?)", "Movie 2")
        .executeAsyncScala())
    dsl2
  }
}

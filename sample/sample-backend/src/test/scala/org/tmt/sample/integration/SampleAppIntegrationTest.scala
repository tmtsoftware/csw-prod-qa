package org.tmt.sample.integration

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import csw.aas.core.commons.AASConnection
import csw.location.api.models.Connection.HttpConnection
import csw.location.api.models._
import csw.location.api.scaladsl.LocationService
import csw.network.utils.Networks
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import io.bullet.borer.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.tmt.embedded_keycloak.KeycloakData.{ApplicationUser, Client, Realm}
import org.tmt.embedded_keycloak.impl.StopHandle
import org.tmt.embedded_keycloak.utils.BearerToken
import org.tmt.embedded_keycloak.{EmbeddedKeycloak, KeycloakData, Settings}
import org.tmt.sample.impl.SampleWiring
import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}
import org.tmt.sample.http.HttpCodecs

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class SampleAppIntegrationTest extends ScalaTestFrameworkTestKit with AnyWordSpecLike with Matchers with HttpCodecs {
  import frameworkTestKit._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds)

  val hostname: String                 = Networks().hostname
  val keycloakPort                     = 8081
  val sampleAppPort                    = 8085
  val sampleWiring                     = new SampleWiring(Some(sampleAppPort))
  val appConnection: HttpConnection    = sampleWiring.settings.httpConnection

  var appLocation: HttpLocation  = _
  var appUri: Uri                = _
  var keycloakHandle: StopHandle = _

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    keycloakHandle = startAndRegisterKeycloak(keycloakPort)
    sampleWiring.start(Metadata.empty).futureValue
    appLocation = locationService.resolve(appConnection, 5.seconds).futureValue.get
    appUri = Uri(appLocation.uri.toString)
  }

  protected override def afterAll(): Unit = {
    keycloakHandle.stop()
    locationService.unregister(AASConnection.value)
    sampleWiring.stop().futureValue
    super.afterAll()
  }

  "SampleWiring" must {

    "start the sample app and register with location service" in {
      val resolvedLocation = locationService.resolve(appConnection, 5.seconds).futureValue
      resolvedLocation.get.connection should ===(appConnection)
    }

    "should call greeting and return GreetResponse as a result" in {
      val userInfo = UserInfo("John", "Smith")
      val request = HttpRequest(
        HttpMethods.POST,
        uri = appUri.withPath(Path / "greeting"),
        entity = HttpEntity(ContentTypes.`application/json`, Json.encode(userInfo).toUtf8String.getBytes)
      )

      val response: HttpResponse = Http().singleRequest(request).futureValue
      response.status should ===(StatusCode.int2StatusCode(200))
      Unmarshal(response).to[GreetResponse].futureValue should ===(GreetResponse(s"Hello user: ${userInfo.firstName} ${userInfo.lastName}!!!"))
    }

    "should call adminGreeting and return AdminGreetResponse as a result" in {
      val token    = getToken("admin", "password1")()
      val userInfo = UserInfo("John", "Smith")
      val request = HttpRequest(
        HttpMethods.POST,
        uri = appUri.withPath(Path / "adminGreeting"),
        headers = token.map(x => Seq(Authorization(OAuth2BearerToken(x)))).getOrElse(Nil),
        entity = HttpEntity(ContentTypes.`application/json`, Json.encode(userInfo).toUtf8String.getBytes())
      )

      val response: HttpResponse = Http().singleRequest(request).futureValue

      response.status should ===(StatusCode.int2StatusCode(200))
      Unmarshal(response).to[AdminGreetResponse].futureValue should ===(
        AdminGreetResponse(s"Hello admin user: ${userInfo.firstName} ${userInfo.lastName}!!!")
      )
    }

    "should call adminGreeting and return 403 as a result without required role" in {
      val token    = getToken("nonAdmin", "password2")()
      val userInfo = UserInfo("John", "Smith")
      val request = HttpRequest(
        HttpMethods.POST,
        uri = appUri.withPath(Path / "adminGreeting"),
        headers = token.map(x => Seq(Authorization(OAuth2BearerToken(x)))).getOrElse(Nil),
        entity = HttpEntity(ContentTypes.`application/json`, Json.encode(userInfo).toUtf8String.getBytes())
      )

      val response: HttpResponse = Http().singleRequest(request).futureValue

      response.status should ===(StatusCode.int2StatusCode(403))
    }
  }

  private def startAndRegisterKeycloak(port: Int): StopHandle = {
    val eswUserRole  = "Esw-user"
    val eswAdminRole = "Esw-admin"
    val locationServerClient =
      Client(name = "tmt-frontend-app", clientType = "public", passwordGrantEnabled = true)
    val keycloakData = KeycloakData(
      realms = Set(
        Realm(
          name = "TMT",
          users = Set(
            ApplicationUser("admin", "password1", realmRoles = Set(eswUserRole, eswAdminRole)),
            ApplicationUser("nonAdmin", "password2")
          ),
          clients = Set(locationServerClient),
          realmRoles = Set(eswUserRole, eswAdminRole)
        )
      )
    )
    val embeddedKeycloak = new EmbeddedKeycloak(keycloakData, Settings(port = port, printProcessLogs = false))
    val stopHandle       = Await.result(embeddedKeycloak.startServer(), 1.minute)
    locationService.register(HttpRegistration(AASConnection.value, keycloakPort, "auth")).futureValue
    stopHandle
  }

  private def getToken(userName: String, password: String): () => Some[String] = { () =>
    Some(
      BearerToken
        .fromServer(
          realm = "TMT",
          client = "tmt-frontend-app",
          host = hostname,
          port = keycloakPort,
          username = userName,
          password = password
        )
        .token
    )
  }

}

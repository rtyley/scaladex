package ch.epfl.scala.index
package server

import model.misc._
import data.github._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.{Unmarshal, Unmarshaller}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.Future

import com.typesafe.config.ConfigFactory

object Response {
  case class AccessToken(access_token: String)


}

case class UserState(repos: Set[GithubRepo],
                     orgs: Set[Organization],
                     user: UserInfo) {
  def isAdmin = orgs.contains(Response.Organization("scalacenter"))
  def isSonatype = orgs.contains(Response.Organization("sonatype"))
  // central-ossrh
  def hasPublishingAuthority = isAdmin || isSonatype
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer)
    extends Json4sSupport {
  import system.dispatcher

  val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.server.oauth2")
  val clientId = config.getString("client-id")
  val clientSecret = config.getString("client-secret")
  val redirectUri = config.getString("uri") + "/callback/done"

  private val poolClientFlow =
    Http().cachedHostConnectionPoolHttps[HttpRequest]("api.github.com")

  def getUserStateWithToken(token: String): Future[UserState] = info(token)
  def getUserStateWithOauth2(code: String): Future[UserState] = {
    def access = {
      Http()
        .singleRequest(
          HttpRequest(
            method = POST,
            uri = Uri("https://github.com/login/oauth/access_token").withQuery(
              Query(
                "client_id" -> clientId,
                "client_secret" -> clientSecret,
                "code" -> code,
                "redirect_uri" -> redirectUri
              )
            ),
            headers = List(Accept(MediaTypes.`application/json`))
          )
        )
        .flatMap(
          response =>
            Unmarshal(response).to[Response.AccessToken].map(_.access_token)
        )
    }

    access.flatMap(info)
  }

  private def info(token: String): Future[UserState] = {
    
  }
}

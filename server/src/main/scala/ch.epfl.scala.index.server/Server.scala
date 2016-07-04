package ch.epfl.scala.index
package server

import data.elastic._

import akka.http.scaladsl.Http
import com.softwaremill.session._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object Server {
  def run(): Http.ServerBinding = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit val sessionManager = new SessionManager[UserState](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserState] {
      def log(msg: String) = println(msg)
    }

    val api = new Api(github)
    val route = new Route(api, github)

    /* wait for elastic to start */
    blockUntilYellow()

    Await.result(Http().bindAndHandle(route.all, "0.0.0.0", 8080), 20.seconds)
  }
  def main(args: Array[String]): Unit = {
    run()
    ()
  }
}
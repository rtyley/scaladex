package ch.epfl.scala.index
package server

import model.Artifact

import akka.http.scaladsl._
import server.Directives._

class RestApi(api: Api) extends Json4sSupport {
  val route = get {
    pathPrefix("api") {
      path("find") {
        parameters('query, 'start.as[Int] ? 0) { (query, start) =>
          complete(api.find(query, start))
        }
      } ~
      path("latest"){
        parameters('organization, 'name) { (organization, name) =>
          complete(api.latest(Artifact.Reference(organization, name)))
        }
      }
    }  
  }
}
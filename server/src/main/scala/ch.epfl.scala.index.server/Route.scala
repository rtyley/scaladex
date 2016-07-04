package ch.epfl.scala.index
package server

import model._
import model.misc.UserInfo
import model.release.SemanticVersion
import data.cleanup.SemanticVersionParser

// import akka.http.scaladsl._
import akka.http.scaladsl.model._
import Uri._
import StatusCodes._

import com.softwaremill.session._
import CsrfDirectives._
import CsrfOptions._
import SessionDirectives._
import SessionOptions._

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._

class Route(api: Api, github: Github)(
  implicit sessionManager: SessionManager[UserState], 
           sessionStorage: InMemoryRefreshTokenStorage[UserState],
           ec: ExecutionContext) {

  private val rest = new RestApi(api)

  val all = {
    import akka.http.scaladsl._
    import server.Directives._
    import TwirlSupport._

    rest.route ~
    get {
      path("login") {
        redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
          "client_id" -> github.clientId
        )),TemporaryRedirect)
      } ~
      path("logout") {
        requiredSession(refreshable, usingCookies) { _ =>
          invalidateSession(refreshable, usingCookies) { ctx =>
            ctx.complete(frontPage(None))
          }
        }
      } ~
      pathPrefix("callback") {
        path("done") {
          complete("OK")
        } ~
        pathEnd {
          parameter('code) { code =>
            val userState = Await.result(github.info(code), 30.seconds)
            setSession(refreshable, usingCookies, userState) {
              setNewCsrfToken(checkHeader) { ctx =>
                ctx.complete(frontPage(Some(userState.user)))
              }
            }
          }
        }
      } ~
      path("assets" / Remaining) { path ⇒
        getFromResource(path)
      } ~
      path("fonts" / Remaining) { path ⇒
        getFromResource(path)
      } ~
      path("search") {
        optionalSession(refreshable, usingCookies) { userState =>
          parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
            complete {
              api.find(query, page, sorting, you.flatMap(_ => userState.map(_.repos)))
                 .map { case (pagination, projects) =>
                   views.html.searchresult(query, sorting, pagination, projects, userState.map(_.user))
                 }
            }
          }
        }
      } ~
      path(Segment) { owner =>
        optionalSession(refreshable, usingCookies) { userState =>
          parameters('artifact, 'version.?){ (artifact, version) =>
            val rest = version match {
              case Some(v) if !v.isEmpty => "/" + v
              case _ => ""
            }
            redirect(s"/$owner/$artifact$rest", StatusCodes.PermanentRedirect)
          } ~
          parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
            complete {
              api.organizationPage(owner, page, sorting)
                 .map { case (pagination, projects) =>
                   views.html.organizationpage(owner, sorting, pagination, projects, userState.map(_.user))
                 }
            }
          }
        }
      } ~
      path(Segment / Segment) { (owner, artifactName) =>
        optionalSession(refreshable, usingCookies) { userState =>
          val reference = Artifact.Reference(owner, artifactName)
          complete(artifactPage(reference, version = None, userState.map(_.user)))
        }
      } ~
      path(Segment / Segment / Segment) { (owner, artifactName, version) =>
        optionalSession(refreshable, usingCookies) { userState =>
          val reference = Artifact.Reference(owner, artifactName)
          complete(artifactPage(reference, SemanticVersionParser(version), userState.map(_.user)))
        }
      } ~
      path("edit" / Segment / Segment) { (owner, artifactName) =>
        optionalSession(refreshable, usingCookies) { userState =>
          val reference = Artifact.Reference(owner, artifactName)
          complete(
            api.projectPage(reference).map(project =>
              project.map(p => views.html.editproject(p, reference, version = None, userState.map(_.user)))
            )
          )
        }
      } ~
      pathSingleSlash {
        optionalSession(refreshable, usingCookies) { userState =>
          complete(frontPage(userState.map(_.user)))
        }
      }
    }
  }
  private def frontPage(userInfo: Option[UserInfo]) = {
    for {
      keywords       <- api.keywords()
      targets        <- api.targets()
      dependencies   <- api.dependencies()
      latestProjects <- api.latestProjects()
      latestReleases <- api.latestReleases()
    } yield views.html.frontpage(keywords, targets, dependencies, latestProjects, latestReleases, userInfo)
  }

  private def artifactPage(reference: Artifact.Reference, version: Option[SemanticVersion], user: Option[UserInfo]) = {
    // This is a list because we still need to filter targets (scala 2.11 vs 2.10 or scalajs, ...)
    def latestStableReleaseOrSelected(project: Project): List[Release] = {
      def latestStableVersion(artifact: Artifact): Option[SemanticVersion] = {
        version match {
          case Some(v) => Some(v)
          case None => {
            val versions =
              artifact.releases
                .map(_.reference.version)
                .sorted.reverse

            // select latest stable release version if applicable
            if(versions.exists(_.preRelease.isEmpty))
              versions.filter(_.preRelease.isEmpty).headOption
            else
              versions.headOption
          }
        }
      }

      for {
        artifact <- project.artifacts.filter(_.reference == reference)
        stableVersion <- latestStableVersion(artifact).toList
        stableRelease <- artifact.releases.filter(_.reference.version == stableVersion)
      } yield stableRelease
    }

    api.projectPage(reference).map(project =>
      project.map{p =>
        val selectedRelease = latestStableReleaseOrSelected(p)
        val selectedVersion =
          version match {
            case None => selectedRelease.headOption.map(_.reference.version)
            case _ => version
          }

        (OK, views.html.artifact(p, reference, selectedVersion, selectedRelease, user))
      }.getOrElse((NotFound, views.html.notfound(user)))
    )
  }
}

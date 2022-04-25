package scaladex.core.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import fastparse.P
import fastparse.Start
import fastparse._
import scaladex.core.api.artifact.ArtifactMetadataResponse
import scaladex.core.model.PatchVersion
import scaladex.core.util.Parsers._

/**
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 */
case class Artifact(
    groupId: Artifact.GroupId,
    // artifactId is redundant with ArtifactName + platform
    // it's kept because we need to do joins with ArtifactDependency on MavenReference
    // It's also possible to create a new key for Artifact: ArtifactReference(GroupId, ArtifactId, SemanticVersion)
    // and keep untyped MavenReference ArtifactDependency.
    artifactId: String,
    version: SemanticVersion,
    artifactName: Artifact.Name,
    projectRef: Project.Reference,
    description: Option[String],
    releaseDate: Instant,
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean,
    platform: Platform,
    language: Language
) {
  val binaryVersion: BinaryVersion = BinaryVersion(platform, language)

  def isValid: Boolean = binaryVersion.isValid

  def groupIdAndName: String = {
    val sep = binaryVersion match {
      case BinaryVersion(Jvm, Java) | BinaryVersion(SbtPlugin(_), _) => ":"
      case BinaryVersion(ScalaJs(_) | ScalaNative(_), _)             => ":::"
      case _                                                         => "::"
    }
    s"$groupId$sep$artifactName"
  }

  private def artifactHttpPath: String = s"/${projectRef.organization}/${projectRef.repository}/$artifactName"

  val mavenReference: Artifact.MavenReference = Artifact.MavenReference(groupId.value, artifactId, version.encode)

  def release: Release =
    Release(projectRef.organization, projectRef.repository, platform, language, version, releaseDate)

  def releaseDateFormat: String = Artifact.dateFormatter.format(releaseDate)

  def fullHttpUrl(env: Env): String =
    env match {
      case Env.Prod => s"https://index.scala-lang.org$artifactHttpPath"
      case Env.Dev =>
        s"https://index-dev.scala-lang.org$artifactHttpPath" // todo: fix locally
      case Env.Local =>
        s"http://localhost:8080$artifactHttpPath" // todo: fix locally
    }

  def httpUrl: String = {
    val binaryVersionQuery = s"?binaryVersion=${binaryVersion.encode}"
    s"$artifactHttpPath/$version$binaryVersionQuery"
  }

  def badgeUrl(env: Env, platform: Option[Platform] = None): String =
    s"${fullHttpUrl(env)}/latest-by-scala-version.svg?platform=${platform.map(_.label).getOrElse(binaryVersion.platform.label)}"

  def latestBadgeUrl(env: Env): String =
    s"${fullHttpUrl(env)}/latest.svg"

  def sbtInstall: String = {
    val install = binaryVersion.platform match {
      case SbtPlugin(_) =>
        s"""addSbtPlugin("$groupId" % "$artifactName" % "$version")"""
      case _ if isNonStandardLib =>
        s"""libraryDependencies += "$groupId" % "$artifactName" % "$version""""
      case ScalaJs(_) | ScalaNative(_) =>
        s"""libraryDependencies += "$groupId" %%% "$artifactName" % "$version""""
      case Jvm =>
        binaryVersion.language match {
          case Java =>
            s"""libraryDependencies += "$groupId" % "$artifactName" % "$version""""
          case Scala(PatchVersion(_, _, _)) =>
            s"""libraryDependencies += "$groupId" % "$artifactName" % "$version" cross CrossVersion.full"""
          case _ =>
            s"""libraryDependencies += "$groupId" %% "$artifactName" % "$version""""
        }
    }

    List(
      Some(install),
      resolver.flatMap(_.sbt.map("resolvers += " + _))
    ).flatten.mkString("\n")
  }

  /**
   * string representation for Ammonite loading
   * @return
   */
  def ammInstall: String = {

    def addResolver(r: Resolver) =
      s"""|import ammonite._, Resolvers._
          |val res = Resolver.Http(
          |  "${r.name}",
          |  "${r.url}",
          |  IvyPattern,
          |  false)
          |interp.resolvers() = interp.resolvers() :+ res""".stripMargin

    val artifactOperator = if (isNonStandardLib) ":" else "::"

    List(
      Some(
        s"import $$ivy.`${groupId}$artifactOperator$artifactName:$version`"
      ),
      resolver.map(addResolver)
    ).flatten.mkString("\n")
  }

  /**
   * string representation for maven dependency
   * @return
   */
  def mavenInstall: String =
    s"""|<dependency>
        |  <groupId>$groupId</groupId>
        |  <artifactId>$artifactId</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin

  /**
   * string representation for gradle dependency
   * @return
   */
  def gradleInstall: String =
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"

  /**
   * string representation for mill dependency
   * @return
   */
  def millInstall: String = {
    def addResolver(r: Resolver): Option[String] =
      r.url.map(url => s"""MavenRepository("${url}")""")
    val artifactOperator = if (isNonStandardLib) ":" else "::"
    List(
      Some(s"""ivy"$groupId$artifactOperator$artifactName:$version""""),
      resolver.flatMap(addResolver)
    ).flatten.mkString("\n")
  }

  def csLaunch: String =
    s"cs launch $groupId:$artifactId:$version"

  def defaultScaladoc: Option[String] =
    resolver match {
      case None => Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
      case _    => None
    }

  // todo: Add tests for this
  def scastieURL: String = {
    val tryBaseUrl = "https://scastie.scala-lang.org/try"

    val targetParam = binaryVersion.platform match {
      case ScalaJs(_) => Some("t" -> "JS")
      case _          => None
    }

    val scalaVersionParam = binaryVersion.language match {
      case Scala(v) => Some("sv" -> v.toString)
      case _        => None
    }

    val params: List[(String, String)] = List(
      "g" -> groupId.value,
      "a" -> artifactName.value,
      "v" -> version.toString
    ) ++ targetParam ++ scalaVersionParam

    params
      .map { case (k, v) => s"$k=$v" }
      .mkString(tryBaseUrl + "?", "&", "")
  }
}

object Artifact {
  private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, uuuu").withZone(ZoneOffset.UTC)

  case class Name(value: String) extends AnyVal {
    override def toString: String = value
  }
  object Name {
    implicit val ordering: Ordering[Name] = Ordering.by(_.value)
  }

  case class GroupId(value: String) extends AnyVal {
    override def toString: String = value
    def mavenUrl: String = value.replace('.', '/')
  }
  case class ArtifactId(name: Name, binaryVersion: BinaryVersion) {
    def value: String = s"${name}${binaryVersion.encode}"
    def isScala: Boolean = binaryVersion.language.isScala
  }

  object ArtifactId {
    import fastparse.NoWhitespace._

    private def FullParser[A: P] = {
      Start ~
        (Alpha | Digit | "-" | "." | (!(BinaryVersion.IntermediateParserButNotInvalidSbt ~ End) ~ "_")).rep.! ~ // must end with scala target
        BinaryVersion.Parser ~
        End
    }.map {
      case (name, binaryVersion) =>
        ArtifactId(Name(name), binaryVersion)
    }

    def parse(artifactId: String): Option[ArtifactId] =
      tryParse(artifactId, x => FullParser(x))
  }

  case class MavenReference(groupId: String, artifactId: String, version: String) {

    def name: String = s"$groupId/$artifactId"

    override def toString(): String = s"$groupId:$artifactId:$version"

    /**
     * url to maven page with related information to this reference
     */
    def searchUrl: String =
      s"https://search.maven.org/#artifactdetails|$groupId|$artifactId|$version|jar"

    def repoUrl: String =
      s"https://repo1.maven.org/maven2/${groupId.replace('.', '/')}/$artifactId/$version/"
  }

  def toMetadataResponse(artifact: Artifact): ArtifactMetadataResponse =
    ArtifactMetadataResponse(
      version = artifact.version.toString,
      projectReference = Some(artifact.projectRef.toString),
      releaseDate = artifact.releaseDate.toString,
      language = artifact.language.toString,
      platform = artifact.platform.toString
    )
}

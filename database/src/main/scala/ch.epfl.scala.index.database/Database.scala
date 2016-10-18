package ch.epfl.scala.index.database

import generated.Tables._

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

class DatabaseSession(jdbcUrl: String, dbUser: String, dbPassword: String) {
  private val hikariConfig = new HikariConfig()
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)

  private val dataSource = new HikariDataSource(hikariConfig)

  val driver = slick.driver.PostgresDriver
  import driver.api._
  val db = Database.forDataSource(dataSource)
  db.createSession()
}


class Database(session: DatabaseSession) {
  import session._



  def save(projectReleases: List[(Project, List[Release])]): Future[Unit] = {

    ProjectsRow(
      id = 0,
      organization: String
      repository: String
      defaultArtifact: String
      defaultStableVersion: Boolean
      customScalaDoc: Option[String] = None
      deprecated: Boolean
      test: Boolean
      contributorsWanted: Boolean
      liveData: Boolean
    )

    // sb.run(Projects ++= )
  }
}
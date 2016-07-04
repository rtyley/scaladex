package ch.epfl.scala.index
package server

import data.elastic._

import utest._

import scala.concurrent.duration._
import scala.concurrent.Await

import org.openqa.selenium._
import firefox.FirefoxDriver
// import org.openqa.selenium.support.ui.ExpectedCondition;
// import org.openqa.selenium.support.ui.WebDriverWait;

object SeleniumSpecs extends TestSuite{
  

  val tests = this{
    "foo"-{
      val binding = Server.run()

      val driver = new FirefoxDriver()
      driver.get("http://localhost:8080")

      assert(true)

      driver.quit()
      esClient.close()
      Await.result(binding.unbind, 20.seconds)
    }
  }

  
}
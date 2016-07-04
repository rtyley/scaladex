package ch.epfl.scala.index
package server

import data.elastic._

import utest._

import scala.concurrent.duration._
import scala.concurrent.Await

import org.openqa.selenium._
import chrome.{ChromeDriver, ChromeOptions}

// import org.openqa.selenium.support.ui.ExpectedCondition;
// import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File

object SeleniumSpecs extends TestSuite{
  

  val tests = this{
    "foo"-{
      val binding = Server.run()

      val driver =
        if(Option(System.getProperty("user.name")) == Some("gui")) {
          val options = new ChromeOptions
          options.setBinary(new File("/run/current-system/sw/bin/chromium"))
          new ChromeDriver(options)
        } else new ChromeDriver
      
      driver.get("http://localhost:8080")

      assert(true)

      driver.quit()
      esClient.close()
      Await.result(binding.unbind, 20.seconds)
    }
  } 
}
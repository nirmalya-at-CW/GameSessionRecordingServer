package scala.org.nirmalya.experiment.gatling

import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.REQStartAGameWith
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.json4s.{DefaultFormats, Formats, ShortTypeHints}
import org.json4s.native.{Json, Serialization}
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.duration._
import org.json4s.{DefaultFormats, Formats, ShortTypeHints, native}


/**
  * Created by nirmalya on 17/8/17.
  */
class ScenarioDriverSimple extends Simulation {

  import org.json4s.DefaultFormats

  implicit val serialization = native.Serialization
  implicit val formats       = Serialization.formats(ShortTypeHints(List(classOf[REQStartAGameWith])))

  val conf = ConfigFactory.load
  println(conf)



  val r = REQStartAGameWith("Codewalla","1","Vikas","P1","G2","Tic-Tac-Toe","A123")
  val rJasonified = Json(DefaultFormats).write[REQStartAGameWith](r)
  def rJsonify(r:REQStartAGameWith): String = Json(DefaultFormats).write[REQStartAGameWith](r)

  val headers_10 = Map("Content-Type" -> "application/json") // Note the headers specific to a given request

  val httpConf = http
    .baseURL("http://127.0.0.1:9090") // Here is the root for all relative URLs
    //.acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
      .headers(headers_10)

  val fedRecords = csv("gamePlaySessionSimulationCases.csv")

  System.exit(-1)

  val scn = scenario("Start A Game") // A scenario is a chain of requests and pauses
      .feed(csv("gamePlaySessionSimulationCases.csv"))
      .exec(session => {

         println(session)
         session
      }

      )
          /*http("request_1")
          .post("/start")
          .body(StringBody(rJasonified)).pause(7) // Note that Gatling has recorded real time pauses
          .exec(http("request_2")
          .get("/computers?f=macbook"))
          .pause(2)
    .exec(http("request_3")
      .get("/computers/6"))
    .pause(3)
    .exec(http("request_4")
      .get("/"))
    .pause(2)
    .exec(http("request_5")
      .get("/computers?p=1"))
    .pause(670 milliseconds)
    .exec(http("request_6")
      .get("/computers?p=2"))
    .pause(629 milliseconds)
    .exec(http("request_7")
      .get("/computers?p=3"))
    .pause(734 milliseconds)
    .exec(http("request_8")
      .get("/computers?p=4"))
    .pause(5)
    .exec(http("request_9")
      .get("/computers/new"))
    .pause(1)
    .exec(http("request_10") // Here's an example of a POST request
      .post("/computers")
      .headers(headers_10)
      .formParam("name", "Beautiful Computer") // Note the triple double quotes: used in Scala for protecting a whole chain of characters (no need for backslash)
      .formParam("introduced", "2012-05-30")
      .formParam("discontinued", "")
      .formParam("company", "37"))*/

  setUp(scn.inject(atOnceUsers(100)).protocols(httpConf))
}
package com.OneHuddle.GamePlaySessionService

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.EmittedEvents._
import com.mashape.unirest.http.Unirest
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats}



import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by nirmalya on 4/12/17.
  */
class AdminPanelNotifierActor extends Actor with ActorLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  type PlayerDetailsTriplet = (String,String,String)

  val pushedAdminPanelEventCollectorEndpoint = context.system.settings.config.
    getConfig("GameSession.externalServices").
    getString("adminPanelPushService")

  var liveGameSessions = Map[String, PlayerDetailsTriplet]()

  // Below, Tuple._1 == ByPlayer, Tuple._2 == ByManager, Tuple._3 == ByTimeOut
  var finishedSessionsCount = (0,0,0)

  override def receive = {

    case ev: EvGameSessionLaunched =>

      val originalSender = sender()
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionLaunched Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")

      if (liveGameSessions.isDefinedAt(ev.data.gameSessionUUID))
        log.info(s"EvGameSessionLaunched Event for Admin Panel received, but gameSessionUUID=${ev.data.gameSessionUUID} seems to be live already.")
      else {

        accumulateLiveSession(ev.data.gameSessionUUID, playerDetails)

        val jsonifiedAdminPanelDataToPush = prepareEventForAdminPanel

        postEventToService(jsonifiedAdminPanelDataToPush)

      }

    case ev: EvGameSessionFinishedByPlayer =>

      val originalSender = sender()
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionFinishedByPlayer Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")

      if (!liveGameSessions.isDefinedAt(ev.data.gameSessionUUID))
        log.info(s"EvGameSessionLaunched Event for Admin Panel received, but gameSessionUUID=${ev.data.gameSessionUUID} seems not to exist.")
      else {

        discardLiveSession(ev.data.gameSessionUUID)

        recordSessionFinishedByPlayer

        val jsonifiedAdminPanelDataToPush = prepareEventForAdminPanel

        postEventToService(jsonifiedAdminPanelDataToPush)

      }

    case ev: EvGameSessionTerminatedByManager =>

      val originalSender = sender()
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionTerminatedByManager Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, manager=${ev.manager}, player=${playerDetails}")

      if (!liveGameSessions.isDefinedAt(ev.data.gameSessionUUID))
        log.info(s"EvGameSessionTerminatedByManager Event for Admin Panel received, but gameSessionUUID=${ev.data.gameSessionUUID} seems not to exist.")
      else {

        discardLiveSession(ev.data.gameSessionUUID)

        recordSessionFinishedByManager

        val jsonifiedAdminPanelDataToPush = prepareEventForAdminPanel

        postEventToService(jsonifiedAdminPanelDataToPush)

      }

    case ev: EvGameSessionTerminatedByTimeOut =>

      val originalSender = sender()
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionTerminatedByTimeOut Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")

      if (!liveGameSessions.isDefinedAt(ev.data.gameSessionUUID))
        log.info(s"EvGameSessionTerminatedByTimeOut Event for Admin Panel received, but gameSessionUUID=${ev.data.gameSessionUUID} seems not to exist.")
      else {

        discardLiveSession(ev.data.gameSessionUUID)

        recordSessionFinishedByTimeOut

        val jsonifiedAdminPanelDataToPush = prepareEventForAdminPanel

        postEventToService(jsonifiedAdminPanelDataToPush)

      }


  }

  private def prepareEventForAdminPanel: String = {

    implicit val formats = DefaultFormats

    val liveSessionsCount = this.liveGameSessions.keySet.size

    val jsonifiedEvent: JObject = ("type", "DATA") ~
          ("content",
            (
                  ("gameSessionsLaunched",            this.liveGameSessions.keySet.size) ~
                  ("gameSessionsFinishedByPlayer",    this.finishedSessionsCount._1)     ~
                  ("gameSessionsFinishedByManager",   this.finishedSessionsCount._2)     ~
                  ("gameSessionsFinishedByTimeout",   this.finishedSessionsCount._3)
            )
          )
    (write(jsonifiedEvent))

  }

  private def accumulateLiveSession(gameSessionUUID: String, p: PlayerDetailsTriplet) = {

    this.liveGameSessions = this.liveGameSessions + (gameSessionUUID -> (p))

  }

  private def discardLiveSession(gameSessionUUID: String) = {

    this.liveGameSessions = this.liveGameSessions - (gameSessionUUID)

  }

  private def recordSessionFinishedByPlayer = {

    this.finishedSessionsCount = (this.finishedSessionsCount._1 + 1, this.finishedSessionsCount._2, this.finishedSessionsCount._3)
  }

  private def recordSessionFinishedByManager = {

    this.finishedSessionsCount = (this.finishedSessionsCount._1, this.finishedSessionsCount._2 + 1, this.finishedSessionsCount._3)
  }

  private def recordSessionFinishedByTimeOut = {

    this.finishedSessionsCount = (this.finishedSessionsCount._1, this.finishedSessionsCount._2, this.finishedSessionsCount._3 + 1)
  }

  private def postEventToService(contentToPost: String) = {

    val  adminPanelServiceCallAction = Future {
      Unirest
        .post(pushedAdminPanelEventCollectorEndpoint)
        .header("Content-Type","application/json")
        .body(contentToPost)
        .asString
    }

    adminPanelServiceCallAction.onComplete {
      case Success(x) =>  log.info(s"Pushing event to AdminPanel: Success, ${x}")
      case Failure(y) =>  log.info(s"Pushing event to AdminPanel: Failure, ${y.getMessage}")
    }
  }

}

object AdminPanelNotifierActor {
  def apply(): Props = Props(new AdminPanelNotifierActor)
}

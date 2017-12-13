package com.OneHuddle.GamePlaySessionService

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{DespatchedToLiveboardAcknowledgement, LiveboardConsumableData}

import org.json4s.native.Serialization._
import com.mashape.unirest.http.Unirest

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by nirmalya on 12/10/17.
  */
class LiveBoardNotifierActor extends Actor with ActorLogging {


  import scala.concurrent.ExecutionContext.Implicits.global

  val liveboardServiceEndpoint = context.system.settings.config.
    getConfig("GameSession.externalServices").
    getString("LiveboardHostingService")

  override def receive = {

    case lb:LiveboardConsumableData =>
      import GameSessionHandlingServiceProtocol.formats_2

      val originalSender = sender

      val jsonifiedLiveboardConsumableData = write[LiveboardConsumableData](lb)

      log.info(s"Ready to inform Liveboard Service about this just finished gameSession ${lb.gameSessionUUID}")

      //"playerID":"Ragha","companyID":"ABC","departmentID":"3","groupID":"6","score":2000.0,"gameID":"GAME1","gameSessionUUID":null,"lastPlayedOn":null,"timezoneApplicable":null,"manager":null

      val lbServiceCallAction = Future {
        Unirest
          .post(liveboardServiceEndpoint)
          .header("Content-Type","application/json")
          .body(jsonifiedLiveboardConsumableData)
          .asString
      }

      lbServiceCallAction.onComplete {

        case Success(x)   =>
          log.info(s"Liveboard acknowledgement, success for Player:GameSession = ${lb.playerID}:${lb.gameSessionUUID}")
          originalSender ! DespatchedToLiveboardAcknowledgement(x.getStatus, x.getStatusText, Some(x.getBody))
        case Failure(ex)  =>
          log.info(s"Liveboard acknowledgement, failure for GameSession = ${lb.gameSessionUUID}, reason: ${ex.getMessage}")
          originalSender ! DespatchedToLiveboardAcknowledgement(-1,"Failed to inform Liveboard",Some(ex.getMessage))

      }



    case m: Any => log.warning(s"Unknown message $m received, expecting data to pass to liveboard")
  }

}

object LiveBoardNotifierActor {

  def apply: Props  = Props(new LiveBoardNotifierActor)

}

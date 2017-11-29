package com.OneHuddle.GamePlaySessionService

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{DespatchedToLeaderboardAcknowledgement, LeaderboardConsumableData}

import org.json4s.native.Serialization._
import com.mashape.unirest.http.Unirest

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by nirmalya on 12/10/17.
  */
class GameSessionCompletionNotifierActor extends Actor with ActorLogging {


  import scala.concurrent.ExecutionContext.Implicits.global

  val leaderboardServiceEndpoint = context.system.settings.config.
    getConfig("GameSession.externalServices").
    getString("LeaderboardHostingService")

  override def receive = {

    case lb:LeaderboardConsumableData =>
      import GameSessionHandlingServiceProtocol.formats_2

      val originalSender = sender

      val jsonifiedLeaderboardConsumableData = write[LeaderboardConsumableData](lb)

      log.info(s"Ready to inform Leaderboard Service about this just finished gameSession ${lb.gameSessionUUID}")

      val lbServiceCallAction = Future {
        Unirest
          .put(leaderboardServiceEndpoint)
          .header("Content-Type","application/json")
          .body(jsonifiedLeaderboardConsumableData)
          .asString
      }

      lbServiceCallAction.onComplete {

        case Success(x)   =>
          log.info(s"Leaderboard acknowledgement, success for Player:GameSession = ${lb.playerID}:${lb.gameSessionUUID}")
          originalSender ! DespatchedToLeaderboardAcknowledgement(x.getStatus, x.getStatusText, Some(x.getBody))
        case Failure(ex)  =>
          log.info(s"Leaderboard acknowledgement, failure for GameSession = ${lb.gameSessionUUID}, reason: ${ex.getMessage}")
          originalSender ! DespatchedToLeaderboardAcknowledgement(-1,"Failed to inform Leaderboard",Some(ex.getMessage))

      }



    case m: Any => log.warning(s"Unknown message $m received, expecting data to pass to leaderboard")
  }

}

object GameSessionCompletionNotifierActor {

  def apply: Props  = Props(new GameSessionCompletionNotifierActor)

}

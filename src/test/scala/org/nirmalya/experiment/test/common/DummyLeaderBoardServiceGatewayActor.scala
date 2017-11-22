package org.nirmalya.experiment.test.common

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{DespatchedToLeaderboardAcknowledgement, LeaderboardConsumableData}

import scala.sys.Prop

/**
  * Created by nirmalya on 1/11/17.
  */
class DummyLeaderBoardServiceGatewayActor(leaderBoardServiceEndpoint: String) extends Actor with ActorLogging {


  override def receive = LoggingReceive.withLabel("Leaderboard"){

    case lb: LeaderboardConsumableData =>

      log.info(s"leaderboard information: ${lb}")
      sender ! DespatchedToLeaderboardAcknowledgement

  }
}

object DummyLeaderBoardServiceGatewayActor {

  def apply(leaderBoardServiceEndpoint: String) = Props(new DummyLeaderBoardServiceGatewayActor(leaderBoardServiceEndpoint))
}

package org.nirmalya.experiment.test.common

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{AckOfDepatchToLeaderBoard, LeaderBoardConsumableData}

import scala.sys.Prop

/**
  * Created by nirmalya on 1/11/17.
  */
class DummyLiveBoardServiceGatewayActor(liveBoardServiceEndpoint: String) extends Actor with ActorLogging {


  override def receive = LoggingReceive.withLabel("Liveboard"){

    case lb: LeaderBoardConsumableData =>

      log.info(s"liveboard information: ${lb}")
      sender ! AckOfDepatchToLeaderBoard

  }
}

object DummyLiveBoardServiceGatewayActor {

  def apply(liveBoardServiceEndpoint: String) = Props(new DummyLiveBoardServiceGatewayActor(liveBoardServiceEndpoint))
}

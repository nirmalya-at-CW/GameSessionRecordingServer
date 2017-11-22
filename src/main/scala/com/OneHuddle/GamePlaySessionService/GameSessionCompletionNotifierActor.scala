package com.OneHuddle.GamePlaySessionService

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.LeaderboardConsumableData

/**
  * Created by nirmalya on 12/10/17.
  */
class GameSessionCompletionNotifierActor extends Actor with ActorLogging {


  val leaderboardServiceEndpoint = context.system.settings.config.
    getConfig("GameSession.externalServices").
    getString("LeaderboardHostingService")

  override def receive = {

    case i:LeaderboardConsumableData =>

      // TODO: replace with actual HTTP call to access Ragha's Leaderboard service
      log.info(s"Ready to inform Leaderboard Service about this just finished gameSession $i")

    case m: Any => log.warning(s"Unknown message $m received, expecting data to pass to leaderboard")
  }

}

object GameSessionCompletionNotifierActor {

  def apply: Props  = Props(new GameSessionCompletionNotifierActor)

}

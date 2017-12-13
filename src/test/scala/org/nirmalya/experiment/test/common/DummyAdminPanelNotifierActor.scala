package org.nirmalya.experiment.test.common

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.EmittedEvents.{EvGameSessionFinishedByPlayer, EvGameSessionLaunched, EvGameSessionTerminatedByManager, EvGameSessionTerminatedByTimeOut}

/**
  * Created by nirmalya on 5/12/17.
  */
class DummyAdminPanelNotifierActor extends Actor with ActorLogging {

  override def receive = {


    case ev: EvGameSessionLaunched =>
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionLaunched Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")

    case ev: EvGameSessionFinishedByPlayer =>
      val originalSender = sender()
      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionFinishedByPlayer Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")

    case ev: EvGameSessionTerminatedByManager =>

      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionTerminatedByManager Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, manager=${ev.manager}, player=${playerDetails}")

    case ev: EvGameSessionTerminatedByTimeOut =>

      val playerDetails = (ev.data.companyID,ev.data.belongsToDepartment,ev.data.playerID)
      log.info(s"EvGameSessionTerminatedByTimeOut Event for Admin Panel received, gameSessionUUID=${ev.data.gameSessionUUID}, player=${playerDetails}")


    case x: Any => log.info(s"Unknown message ${x} received.")
  }

}

object DummyAdminPanelNotifierActor {

  def apply() = Props(new DummyAdminPanelNotifierActor())
}

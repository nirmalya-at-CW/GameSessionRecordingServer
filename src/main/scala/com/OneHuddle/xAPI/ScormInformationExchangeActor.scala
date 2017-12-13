package com.OneHuddle.xAPI

import java.net.URI
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.PlayerPerformanceRecordSP
import com.rusticisoftware.tincan._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by nirmalya on 10/12/17.
  */
case class LRSAgent(mbox: String) {
  val a = new Agent()
  a.setMbox(s"mailto:${mbox}@1huddle.co")
  a.setName("1HuddleAgent")
  def xapiAgent = a
}

trait LRSVerb { val id: String = "http://adlnet.gov/expapi/verbs"; def xapiVerb: Verb }
case object OneHuddleVerbAttempted extends LRSVerb {
  val name = "Attempted"
  val v = new Verb(id + "/" + name)
  v.setDisplay(new LanguageMap())
  v.getDisplay().put("en-US", name)
  override def xapiVerb = v

}

case object OneHuddleVerbMastered extends LRSVerb {
  val name = "Mastered"
  val v = new Verb(id + "/" + name)
  v.setDisplay(new LanguageMap())
  v.getDisplay().put("en-US", name)
  override def xapiVerb = v

}

case object OneHuddleVerbFailed extends LRSVerb {
  val name = "Failed"
  val v = new Verb(id + "/" + name)
  v.setDisplay(new LanguageMap())
  v.getDisplay().put("en-US", name)
  override def xapiVerb = v

}


trait LRSActivity { val id: String; def xapiActivity: Activity}
case class OneHuddleActivityGamePlay(val companyID: String, val departmentID: String, val playerID: String) extends LRSActivity {

  val id = "http://1huddle.co/activities/gamePlay"
  val activity = new Activity()
  activity.setId(new URI(id))
  activity.setDefinition(new ActivityDefinition())
  activity.getDefinition().setType(new URI("http://id.1huddle.co/activitytype/regsp"))
  activity.getDefinition().setName(new LanguageMap())
  activity.getDefinition().getName().put("en-US", "1Huddle.GAME1")
  activity.getDefinition().setDescription(new LanguageMap())
  activity.getDefinition().getDescription().put("en-US", "1Huddle Regular Singleplayer Game")

  override def xapiActivity: Activity = activity
}


case class LRSStatement(lrsAgent: LRSAgent, lrsVerb: LRSVerb, activity: LRSActivity) {
  val s = new Statement()
  s.setId(UUID.randomUUID())
  s.setActor(lrsAgent.xapiAgent)
  s.setVerb(lrsVerb.xapiVerb)
  s.setObject(activity.xapiActivity)
  def getStatement = s
}


case class ExternalLRS(val lrsEndpoint: String, val lrsUser: String, val lrsPassword: String) {

  val lrs = new RemoteLRS(TCAPIVersion.V101)
  lrs.setEndpoint(lrsEndpoint)
  lrs.setUsername(lrsUser)
  lrs.setPassword(lrsPassword)

}

sealed trait LRSConsumableMessage
case class UpdateLRSGamePlayed(rec: PlayerPerformanceRecordSP) extends LRSConsumableMessage

class ScormInformationExchangeActor (
        val lrsEndpoint: String, val lrsVersion: String, val lrsUser: String, val lrsPassword: String)
  extends Actor with ActorLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  val externalLRS = ExternalLRS(lrsEndpoint,lrsUser,lrsPassword)

  override def receive =  {

    case u: UpdateLRSGamePlayed =>

      val s = LRSStatement(
        LRSAgent(s"${u.rec.companyID}.${u.rec.belongsToDepartment}.${u.rec.playerID}"),
        deduceVerb(u),
        OneHuddleActivityGamePlay(u.rec.companyID,u.rec.belongsToDepartment,u.rec.playerID)
      )

      log.info(s"lrs: ${externalLRS.lrs}")
      log.info(s"statement: ${s.getStatement}, agent: ${s.getStatement.getActor}, verb: ${s.getStatement.getVerb}")



      val lrsInteraction = Future {

        externalLRS.lrs.saveStatement(s.getStatement)
      }

      lrsInteraction.onComplete {

        case Success(e) =>

          val saveActionStatus = if (e.getSuccess) "Success" else "Failure"
          log.info(s"LRS Save Action: ${saveActionStatus}, ${e.getContent} ")
        case Failure(ex) =>

          log.info(s"Call to LRS ${lrsEndpoint} failed, ${ex.getMessage}")

      }
  }

  private def deduceVerb(u: UpdateLRSGamePlayed): LRSVerb = {

    if (u.rec.pointsObtained == 600) OneHuddleVerbMastered
    else if (u.rec.pointsObtained <= 200) OneHuddleVerbFailed
          else OneHuddleVerbAttempted

  }

}

object ScormInformationExchangeActor {

  def apply(lrsEndpoint: String, lrsVersion: String, lrsUser: String, lrsPassword: String): Props =
    Props(new ScormInformationExchangeActor(lrsEndpoint, lrsVersion, lrsUser, lrsPassword))
}
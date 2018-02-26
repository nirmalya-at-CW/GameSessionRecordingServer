package com.OneHuddle.GamePlaySessionService.MariaDBAware

import java.sql.{Connection, DriverManager}

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.jOOQ.generated.Tables._
import com.typesafe.config.ConfigFactory

import collection.JavaConverters._
import org.jooq.SQLDialect
import org.jooq.impl.DSL

import scala.concurrent.ExecutionContextExecutor


case class PlayerDetails(companyID: String, belongsToDepartment: String, playerID: String,
                                  companyName: String, playerName: String, playerEMailID: String,
                                  applicableTimeZone: String, belongsToGroup: String)

case class DBActionPlayerToRetrieve(companyID: String, departmentName: String, playerID: String)
case class DBActionPlayerToUpdate(companyID: String, belongsToDepartment: String, playerID: String,
                          companyName: String, playerName: String, playerEMailID: String,
                          applicableTimeZone: String, belongsToGroup: String)
case class DBActionPlayerToInsert(companyID: String, belongsToDepartment: String, playerID: String,
                                  companyName: String, playerName: String, playerEMailID: String,
                                  applicableTimeZone: String, belongsToGroup: String)

/**
  * Created by nirmalya on 4/10/17.
  */
class PlayerDetailsButlerActor(
            val dbAccessURL:   String,
            val dbAccessDispatcher: ExecutionContextExecutor
      ) extends Actor with ActorLogging {



  override def receive: Receive =  {

    case r: DBActionPlayerToRetrieve => // TBD

  }

}

object PlayerDetailsButlerActor extends JOOQDBDialectDeterminer {

  val localConfig =  ConfigFactory.load()

  val jooqDialectString =
    localConfig
      .getConfig("GameSession.database")
      .getString("jOOQDBDialect")

  val dialectToUse = chooseDialect(if (jooqDialectString.isEmpty) "MARIADB" else jooqDialectString)

  def apply(dbAccessURL: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new LeaderBoardSnapshotDBButlerActor(dbAccessURL,dbAccessDispatcher))

  def retrieve(c: Connection, companyName: String, department: String, playerID: String): List[PlayerDetails] = {

    val e = DSL.using(c, dialectToUse)                             //
    val x = PLAYERDETAILS as "x"

    val k = e.select()
      .from(PLAYERDETAILS)
      .where(PLAYERDETAILS.PLAYERID.eq("P002"))
      .fetchInto(classOf[PlayerDetails])
      .asScala
      .toList

    if (k.isEmpty) List(NonExistentPlayerDetails) else k

  }



}

object NonExistentPlayerDetails extends PlayerDetails("Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown")

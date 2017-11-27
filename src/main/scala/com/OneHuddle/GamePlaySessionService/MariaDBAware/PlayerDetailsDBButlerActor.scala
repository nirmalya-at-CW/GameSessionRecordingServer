package com.OneHuddle.GamePlaySessionService.MariaDBAware

import java.sql.{Connection, DriverManager}

import akka.actor.{Actor, ActorLogging, Props}


import com.OneHuddle.GamePlaySessionService.jOOQ.generated.Tables._
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

object PlayerDetailsButlerActor {

  def apply(dbAccessURL: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionDBButlerActor(dbAccessURL,dbAccessDispatcher))

  def retrieve(c: Connection, companyName: String, department: String, playerID: String): List[PlayerDetails] = {

    val c = DriverManager.getConnection("jdbc:mariadb://localhost:3306/OneHuddle","nuovo","nuovo123")
    val e = DSL.using(c, SQLDialect.MARIADB)                             //
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

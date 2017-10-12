package example.org.nirmalya.experiments.MariaDBAware

import java.sql.{Connection, DriverManager}

import akka.actor.{Actor, ActorLogging, Props}


import collection.JavaConverters._
import org.jooq.scalaextensions.Conversions._

import org.jooq.SQLDialect
import org.jooq.impl.DSL

import scala.concurrent.ExecutionContextExecutor

import generated.Tables._

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
            val connectionString:   String,
            val dbAccessDispatcher: ExecutionContextExecutor
      ) extends Actor with ActorLogging {



  override def receive: Receive =  {

    case r: DBActionPlayerToRetrieve =>

      val rows = GameSessionRecordButlerActor
                 .retrieve(
                   DriverManager.getConnection(connectionString),
                   r.companyID,
                   r.departmentName,
                   r.playerID
                 )

      sender ! rows

    case u: DBActionPlayerPerformanceToUpsert   =>

  }

}

object PlayerDetailsButlerActor {

  def apply(connectionString: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionRecordButlerActor(connectionString,dbAccessDispatcher))

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

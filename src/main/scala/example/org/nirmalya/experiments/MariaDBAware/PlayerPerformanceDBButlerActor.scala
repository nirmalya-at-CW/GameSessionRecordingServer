package example.org.nirmalya.experiments.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.LocalDateTime

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import generated.Tables._

import collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor

case class DBActionPlayerPerformanceToRetrieveAndUpsert(
                 companyID: String, belongsToDepartment: String, playerID: String, gameID: String,
                 companyName: String, manager: String, belongsToGroup: String, gameName: String,
                 lastPlayedOn: LocalDateTime, timezoneApplicable: String,  scoreSoFar: Int)

/**
  * Created by nirmalya on 4/10/17.
  */

case class PlayerPerformance(companyID: String, belongsToDepartment: String, playerID: String, gameID: String,
                             companyName: String, manager: String, belongsToGroup: String, gameName: String,
                             lastPlayedOn: LocalDateTime, timezoneApplicable: String,  scoreSoFar: Int)

class PlayerPerformanceButlerActor(
            val connectionString:   String,
            val dbAccessDispatcher: ExecutionContextExecutor
      ) extends Actor with ActorLogging {



  override def receive: Receive =  {

    case r: DBActionGameSessionRecordInsert =>

      val rows = GameSessionRecordButlerActor
                 .upsert(
                   DriverManager.getConnection(connectionString),
                   r
                 )

      sender ! rows

    case u: DBActionPlayerToUpdate   =>

  }

}

object PlayerPerformanceButlerActor {

  def apply(connectionString: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionRecordButlerActor(connectionString,dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String)
      : List[GameSessionRecord] = {

    val e = DSL.using(c, SQLDialect.MARIADB)
    val x = PLAYERPERFORMANCE as "x"

    val k = e.selectFrom(PLAYERPERFORMANCE)
      .where(PLAYERPERFORMANCE.COMPANYID.eq(companyID))
      .and(PLAYERPERFORMANCE.BELONGSTODEPARTMENT.eq(department))
      .and(PLAYERPERFORMANCE.GAMEID.eq(gameID))
      .and(PLAYERPERFORMANCE.PLAYERID.eq(playerID))
      .fetchInto(classOf[GameSessionRecord])
      .asScala
      .toList

    // TODO:
    // Consider retrieving a record of PlayerPerformance and treat that as a Record type.
    // What if we return a list of Records from this method, keeping the equivalence between
    // Select and Upsert (below)? Does that bring any extra cleaner interface?

    if (k.isEmpty) List(NonExistentGameSessionRecord) else k

  }

  def upsert(
        c: Connection, playerPerformanceData: DBActionGameSessionRecordInsert
      ) = {

    val e = DSL.using(c, SQLDialect.MARIADB)

    val playerPerformanceRecord = e.newRecord(PLAYERPERFORMANCE)

    playerPerformanceRecord.setCompanyid               (playerPerformanceData.companyID)
    playerPerformanceRecord.setBelongstodepartment     (playerPerformanceData.belongsToDepartment)
    playerPerformanceRecord.setPlayerid                (playerPerformanceData.playerID)
    playerPerformanceRecord.setGameid                  (playerPerformanceData.gameID)
    playerPerformanceRecord.setCompanyname             (playerPerformanceData.companyName)
    playerPerformanceRecord.setManager                 (playerPerformanceData.manager)
    playerPerformanceRecord.setGamename                (playerPerformanceData.gameName)
    playerPerformanceRecord.setBelongstogroup          (playerPerformanceData.belongsToGroup)
    playerPerformanceRecord.setLastplayedon            (Timestamp.valueOf(playerPerformanceData.lastPlayedOn))
    playerPerformanceRecord.setTimezoneapplicable      (playerPerformanceData.timezoneApplicable)
    playerPerformanceRecord.setScoresofar              (playerPerformanceData.score)


    e.insertInto(PLAYERPERFORMANCE)
       .set(playerPerformanceRecord)
       .onDuplicateKeyUpdate()
       .set(playerPerformanceRecord)
       .execute()

  }

}

object NonExistentPlayerPerformance extends
  GameSessionRecord("Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown",LocalDateTime.MIN,"Unknown",-1)


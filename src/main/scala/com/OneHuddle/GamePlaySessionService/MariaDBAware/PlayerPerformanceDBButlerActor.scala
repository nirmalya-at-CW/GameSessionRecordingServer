package com.OneHuddle.GamePlaySessionService.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.DBHatch.{DBActionGameSessionRecord, DBActionInsertFailure, DBActionInsertSuccess, DBActionPlayerPerformanceRecord}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.PlayerPerformanceRecordSP
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import com.OneHuddle.GamePlaySessionService.jOOQ.generated.Tables._
import com.typesafe.config.ConfigFactory

import collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor



/**
  * Created by nirmalya on 4/10/17.
  */


class PlayerPerformanceDBButlerActor(
            val connectionString:   String,
            val dbAccessDispatcher: ExecutionContextExecutor
      ) extends Actor with ActorLogging {



  override def receive: Receive =  {

    case r: PlayerPerformanceRecordSP =>

      log.info(s"Database action=Insert, PlayerPerformance, Player = ${r.companyID}.${r.belongsToDepartment}.${r.playerID} starts.")

      val sessionPlayedOnUTC = r.lastPlayedOn.withZoneSameInstant(ZoneOffset.UTC)

      val dbRecord = DBActionPlayerPerformanceRecord(
        r.companyID,
        r.belongsToDepartment,
        r.playerID,
        r.gameID,
        r.gameType,
        r.groupID,
        r.lastPlayedOn.toLocalDateTime,
        r.timezoneApplicable,
        r.pointsObtained,
        r.timeTaken,
        r.winsAchieved
      )
      val rows = PlayerPerformanceDBButlerActor
                 .upsert(
                   DriverManager.getConnection(connectionString),
                   dbRecord
                 )

      sender ! (if (rows == 1) DBActionInsertSuccess(1) else DBActionInsertFailure(s"$rows inserted"))

    case u: DBActionPlayerToUpdate   =>

  }

}

object PlayerPerformanceDBButlerActor extends JOOQDBDialectDeterminer {

  val localConfig =  ConfigFactory.load()

  val jooqDialectString =
    localConfig
      .getConfig("GameSession.database")
      .getString("jOOQDBDialect")

  val dialectToUse = chooseDialect(if (jooqDialectString.isEmpty) "MARIADB" else jooqDialectString)

  def apply(dbAccessURL: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new PlayerPerformanceDBButlerActor(dbAccessURL, dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String)
      : List[DBActionPlayerPerformanceRecord] = {

    // val e = DSL.using(c, SQLDialect.MARIADB)
    val e = DSL.using(c, dialectToUse)
    val x = PLAYERPERFORMANCE as "x"

    val k = e.selectFrom(PLAYERPERFORMANCE)
      .where(PLAYERPERFORMANCE.COMPANYID.eq(companyID))
      .and(PLAYERPERFORMANCE.BELONGSTODEPARTMENT.eq(department))
      .and(PLAYERPERFORMANCE.GAMEID.eq(gameID))
      .and(PLAYERPERFORMANCE.PLAYERID.eq(playerID))
      .fetchInto(classOf[DBActionPlayerPerformanceRecord])
      .asScala
      .toList

    // TODO:
    // Consider retrieving a record of PlayerPerformance and treat that as a Record type.
    // What if we return a list of Records from this method, keeping the equivalence between
    // Select and Upsert (below)? Does that bring any extra cleaner interface?

    if (k.isEmpty) List(NonExistentPlayerPerformance) else k

  }

  def upsert(
        c: Connection, playerPerformanceData: DBActionPlayerPerformanceRecord
      ) = {

    // val e = DSL.using(c, SQLDialect.MARIADB)
    val e = DSL.using(c, SQLDialect.MYSQL_5_7)

    val playerPerformanceRecord = e.newRecord(PLAYERPERFORMANCE)

    playerPerformanceRecord.setCompanyid               (playerPerformanceData.companyID)
    playerPerformanceRecord.setBelongstodepartment     (playerPerformanceData.belongsToDepartment)
    playerPerformanceRecord.setPlayerid                (playerPerformanceData.playerID)
    playerPerformanceRecord.setGameid                  (playerPerformanceData.gameID)
    playerPerformanceRecord.setGroupid                 (playerPerformanceData.groupID)
    playerPerformanceRecord.setLastplayedon            (Timestamp.valueOf(playerPerformanceData.lastPlayedOn))
    playerPerformanceRecord.setTimezoneapplicable      (playerPerformanceData.timezoneApplicable)
    playerPerformanceRecord.setPointsobtained          (playerPerformanceData.pointsObtained)
    playerPerformanceRecord.setTimetaken               (playerPerformanceData.timeTaken)
    playerPerformanceRecord.setWinsachieved            (playerPerformanceData.winsAchieved)


    e.insertInto(PLAYERPERFORMANCE)
       .set(playerPerformanceRecord)
       .execute()

  }

}

object NonExistentPlayerPerformance extends
  DBActionPlayerPerformanceRecord(
    "Unknown","Unknown","Unknown","Unknown","Unknown","Unknown",LocalDateTime.now(ZoneId.of("Z")),"Unknown",-1,-1,-1)


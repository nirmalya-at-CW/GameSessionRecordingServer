package com.OneHuddle.GamePlaySessionService.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}


import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.DBHatch._
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import com.OneHuddle.GamePlaySessionService.jOOQ.generated.Tables._


import java.time._

import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ComputedGameSessionRegSP

import collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor


/**
  * Created by nirmalya on 4/10/17.
  */



class GameSessionDBButlerActor(dbAccessURL: String, val dbAccessDispatcher: ExecutionContextExecutor) extends Actor with ActorLogging {


  log.info(s"DBButler for GameSession, initialized with driverURL:($dbAccessURL)")

  override def receive: Receive =  {

    case finishedAndComputedGameSession : ComputedGameSessionRegSP =>

      log.info(s"Database action=Insert, GameSession=${finishedAndComputedGameSession.gameSessionUUID} starts.")

      val sessionStartedAtUTC = finishedAndComputedGameSession.startedAt.withZoneSameInstant(ZoneOffset.UTC)
      val sessionEndedAtUTC = finishedAndComputedGameSession.finishedAt.withZoneSameInstant(ZoneOffset.UTC)

      val dbRecord = DBActionGameSessionRecord(
        finishedAndComputedGameSession.companyID,
        finishedAndComputedGameSession.departmentID,
        finishedAndComputedGameSession.playerID,
        finishedAndComputedGameSession.gameID,
        finishedAndComputedGameSession.gameSessionUUID,
        finishedAndComputedGameSession.departmentID,
        finishedAndComputedGameSession.gameType,
        gameName = "NOTSUPPLIED",
        sessionStartedAtUTC.toLocalDateTime,
        sessionEndedAtUTC.toLocalDateTime,
        finishedAndComputedGameSession.timezoneApplicable,
        finishedAndComputedGameSession.endedBecauseOf.toString,
        finishedAndComputedGameSession.totalPointsObtained,
        finishedAndComputedGameSession.timeTakenToFinish
      )

      val rows = GameSessionDBButlerActor
                 .upsert(
                   DriverManager.getConnection(dbAccessURL),
                   dbRecord
                 )

      sender ! (if (rows == 1) DBActionInsertSuccess(1) else DBActionInsertFailure(s"$rows inserted"))

    case m: Any   => log.info("Unknown message $m received, no database action possible." )


  }

}

object GameSessionDBButlerActor {

  def apply(dbAccessURL: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

    Props(new GameSessionDBButlerActor(dbAccessURL, dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String, gameSessionUUID: String)
      : List[ComputedGameSessionRegSP] = {

    val e = DSL.using(c, SQLDialect.MARIADB)

    val k = e.selectFrom(GAMESESSIONRECORDS)
      .where(GAMESESSIONRECORDS.COMPANYID.eq(companyID))
      .and(GAMESESSIONRECORDS.BELONGSTODEPARTMENT.eq(department))
      .and(GAMESESSIONRECORDS.GAMEID.eq(gameID))
      .and(GAMESESSIONRECORDS.PLAYERID.eq(playerID))
      .and(GAMESESSIONRECORDS.GAMESESSIONUUID.eq(gameSessionUUID))
      .fetchInto(classOf[DBActionGameSessionRecord])
      .asScala
      .toList

    // TODO: We are taking particular care to explicitly closing a connection below.
    // TODO: However, we have to find out a good functional approach to emulate Java's
    // TODO: Try-with-resources here (in Scala World, it is called ARM: Automatic
    // TODO: Resource Management
    c.close()

    transformDBRecordToComputedGameSession(
      if (k.isEmpty)
        List(NonExistentGameSessionRecord)
      else k
    )

  }

  def upsert(c: Connection, gameSessionRecordableData: DBActionGameSessionRecord) = {

    val e = DSL.using(c, SQLDialect.MARIADB)

    val gameSessionRecord = e.newRecord(GAMESESSIONRECORDS)

    gameSessionRecord.setCompanyid               (gameSessionRecordableData.companyID)
    gameSessionRecord.setBelongstodepartment     (gameSessionRecordableData.belongsToDepartment)
    gameSessionRecord.setPlayerid                (gameSessionRecordableData.playerID)
    gameSessionRecord.setGameid                  (gameSessionRecordableData.gameID)
    gameSessionRecord.setGamesessionuuid         (gameSessionRecordableData.gameSessionUUID)
    gameSessionRecord.setBelongstogroup          (gameSessionRecordableData.belongsToGroup)
    gameSessionRecord.setGametype                (gameSessionRecordableData.gameType)
    gameSessionRecord.setGamename                (gameSessionRecordableData.gameName)
    gameSessionRecord.setStartedatinutc          (Timestamp.valueOf(gameSessionRecordableData.startedAtInUTC))
    gameSessionRecord.setFinishedatinutc         (Timestamp.valueOf(gameSessionRecordableData.finishedAtInUTC))
    gameSessionRecord.setTimezoneapplicable      (gameSessionRecordableData.timezoneApplicable)
    gameSessionRecord.setEndreason               (gameSessionRecordableData.endReason)
    gameSessionRecord.setScore                   (gameSessionRecordableData.score)
    gameSessionRecord.setTimetaken               (gameSessionRecordableData.timeTaken)

    val result = e.insertInto(GAMESESSIONRECORDS)
       .set(gameSessionRecord)
       .execute()

    // TODO: We are taking particular care to explicitly closing a connection below.
    // TODO: However, we have to find out a good functional approach to emulate Java's
    // TODO: Try-with-resources here (in Scala World, it is called ARM: Automatic
    // TODO: Resource Management
    c.close()

    result

  }

  private def transformDBRecordToComputedGameSession(l: List[DBActionGameSessionRecord]) = {

    l.map(nextRec => ComputedGameSessionRegSP(
      nextRec.companyID,
      nextRec.belongsToDepartment,
      nextRec.playerID,
      nextRec.gameID,
      nextRec.gameSessionUUID,
      nextRec.belongsToGroup,
      (ZonedDateTime.of(
        nextRec.startedAtInUTC,ZoneId.of("UTC"))
        .withZoneSameInstant(ZoneId.of(nextRec.timezoneApplicable))),
      (ZonedDateTime.of(
        nextRec.finishedAtInUTC,ZoneId.of("UTC"))
        .withZoneSameInstant(ZoneId.of(nextRec.timezoneApplicable))),
      nextRec.timezoneApplicable,
      nextRec.score,
      nextRec.timeTaken,
      nextRec.endReason
    ))
  }


}

// TODO: Move this to the protocol set
object NonExistentGameSessionRecord extends
  DBActionGameSessionRecord(
    "Unknown CompanyID",
    "Unknown Department",
    "Unknown PlayerID",
    "Unknown GameID",
    "Unknown GameSessionUUID",
    "Unknown Group",
    "Unknown GameType",
    "Unknwon GameName",
    Instant.ofEpochMilli(System.currentTimeMillis).atZone(ZoneId.of("UTC")).toLocalDateTime,
    Instant.ofEpochMilli(System.currentTimeMillis).atZone(ZoneId.of("UTC")).toLocalDateTime,
    "UTC",
    "Unknown EndReason",
    -1,
    -11
  )

object NonExistentComputedGameSession extends
  ComputedGameSessionRegSP(
    "Unknown CompanyID",
    "Unknown Department",
    "Unknown PlayerID",
    "Unknown GameID",
    "Unknown GameSessionUUID",
    "Unknown Group",
    Instant.ofEpochMilli(System.currentTimeMillis).atZone(ZoneId.of("UTC")),
    Instant.ofEpochMilli(System.currentTimeMillis).atZone(ZoneId.of("UTC")),
    "UTC",
    -1,
    -11,
    "Unknown EndReason"
  )

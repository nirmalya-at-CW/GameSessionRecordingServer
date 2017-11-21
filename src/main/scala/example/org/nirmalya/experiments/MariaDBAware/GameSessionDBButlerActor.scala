package example.org.nirmalya.experiments.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}


import akka.actor.{Actor, ActorLogging, Props}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.DBHatch._
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import generated.Tables._

import collection.JavaConverters._
import java.time._

import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ComputedGameSession

import scala.concurrent.ExecutionContextExecutor


/**
  * Created by nirmalya on 4/10/17.
  */



class GameSessionDBButlerActor(val dbAccessDispatcher: ExecutionContextExecutor) extends Actor with ActorLogging {


  val dbAccessURL = context.system.settings.config.getConfig("GameSession.externalServices").getString("dbAccessURL")

  printf(s" **** dbAcccessURL = $dbAccessURL")

  override def receive: Receive =  {

    case finishedAndComputedGameSession: ComputedGameSession =>

      log.info(s"Database action=Insert, GameSession=${finishedAndComputedGameSession.gameSessionUUID} starts.")

      val sessionEndedAtUTC = finishedAndComputedGameSession.completedAt.withZoneSameInstant(ZoneOffset.UTC)

      val dbRecord = DBActionGameSessionRecord(
        finishedAndComputedGameSession.companyID,
        finishedAndComputedGameSession.departmentID,
        finishedAndComputedGameSession.playerID,
        finishedAndComputedGameSession.gameID,
        finishedAndComputedGameSession.gameSessionUUID,
        finishedAndComputedGameSession.departmentID,
        finishedAndComputedGameSession.gameType,
        gameName = "NOTSUPPLIED",
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

    case u: DBActionPlayerToUpdate   =>


  }

}

object GameSessionDBButlerActor {

  def apply(dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionDBButlerActor(dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String, gameSessionUUID: String)
      : List[ComputedGameSession] = {

    val e = DSL.using(c, SQLDialect.MARIADB)
    val x = GAMESESSIONRECORDS as "x"

    val k = e.selectFrom(GAMESESSIONRECORDS)
      .where(GAMESESSIONRECORDS.COMPANYID.eq(companyID))
      .and(GAMESESSIONRECORDS.BELONGSTODEPARTMENT.eq(department))
      .and(GAMESESSIONRECORDS.GAMEID.eq(gameID))
      .and(GAMESESSIONRECORDS.PLAYERID.eq(playerID))
      .and(GAMESESSIONRECORDS.GAMESESSIONUUID.eq(gameSessionUUID))
      .fetchInto(classOf[DBActionGameSessionRecord])
      .asScala
      .toList

    // TODO:
    // Consider retrieving a record of PlayerPerformance and treat that as a Record type.
    // What if we return a list of Records from this method, keeping the equivalence between
    // Select and Upsert (below)? Does that bring any extra cleaner interface?

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
    gameSessionRecord.setLastplayedoninutc       (Timestamp.valueOf(gameSessionRecordableData.lastPlayedOnInUTC))
    gameSessionRecord.setTimezoneapplicable      (gameSessionRecordableData.timezoneApplicable)
    gameSessionRecord.setEndreason               (gameSessionRecordableData.endReason)
    gameSessionRecord.setScore                   (gameSessionRecordableData.score)

    e.insertInto(GAMESESSIONRECORDS)
       .set(gameSessionRecord)
       .execute()

  }

  private def transformDBRecordToComputedGameSession(l: List[DBActionGameSessionRecord]) = {

    l.map(nextRec => ComputedGameSession(
      nextRec.companyID,
      nextRec.belongsToDepartment,
      nextRec.playerID,
      nextRec.gameID,
      nextRec.gameType,
      nextRec.gameSessionUUID,
      nextRec.belongsToGroup,
      (ZonedDateTime.of(
        nextRec.lastPlayedOnInUTC,ZoneId.of("UTC"))
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
    "UTC",
    "Unknown EndReason",
    -1,
    -11
  )

object NonExistentComputedGameSession extends
  ComputedGameSession(
    "Unknown CompanyID",
    "Unknown Department",
    "Unknown PlayerID",
    "Unknown GameID",
    "Unknown GameSessionUUID",
    "Unknown Group",
    "Unknown GameType",
    Instant.ofEpochMilli(System.currentTimeMillis).atZone(ZoneId.of("UTC")),
    "UTC",
    -1,
    -11,
    "Unknown EndReason"
  )

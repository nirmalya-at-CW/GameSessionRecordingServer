package com.OneHuddle.GamePlaySessionService.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}

import akka.actor.{Actor, ActorLogging, Props}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.DBHatch._
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import com.OneHuddle.GamePlaySessionService.jOOQ.generated.Tables._
import java.time._

import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{ComputedGameSessionRegSP, LiveBoardSnapshot}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor


/**
  * Created by nirmalya on 4/10/17.
  */



class LiveBoardSnapshotDBButlerActor(
          dbAccessURL: String, val dbAccessDispatcher: ExecutionContextExecutor)
      extends Actor with ActorLogging {


  log.info(s"DBButler for LiveBoardSnapshot, initialized with driverURL:($dbAccessURL)")

  override def receive: Receive =  {

    case liveBoardSnapshot: LiveBoardSnapshot =>

      val snapshotID = new StringBuffer()
                           .append(liveBoardSnapshot.companyID)
                           .append(".")
                           .append(liveBoardSnapshot.departmentID)
                           .append(".")
                           .append(liveBoardSnapshot.playerID)
                           .toString
      log.info(s"Database action=Insert, LiveBoardSnapshot=${snapshotID} starts.")

      val snapshotTakenAtUTC = liveBoardSnapshot.takenAt.withZoneSameInstant(ZoneOffset.UTC)

      val dbRecord = DBActionLiveBoardSnapshotRecord(
        snapshotTakenAtUTC.toLocalDateTime,
        liveBoardSnapshot.timezoneApplicable,
        liveBoardSnapshot.companyID,
        liveBoardSnapshot.departmentID,
        liveBoardSnapshot.playerID,
        liveBoardSnapshot.gameID,
        liveBoardSnapshot.gameType,
        liveBoardSnapshot.gameType,
        liveBoardSnapshot.rankComputed
      )

      val rows = LiveBoardSnapshotDBButlerActor
                 .upsert(
                   DriverManager.getConnection(dbAccessURL),
                   dbRecord
                 )

      sender ! (if (rows == 1) DBActionInsertSuccess(1) else DBActionInsertFailure(s"$rows inserted"))

  }

}

object LiveBoardSnapshotDBButlerActor extends JOOQDBDialectDeterminer {

  val localConfig =  ConfigFactory.load()

  val jooqDialectString =
    localConfig
      .getConfig("GameSession.database")
      .getString("jOOQDBDialect")

  val dialectToUse = chooseDialect(if (jooqDialectString.isEmpty) "MARIADB" else jooqDialectString)

  def apply(dbAccessURL: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

    Props(new LiveBoardSnapshotDBButlerActor(dbAccessURL, dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String, gameSessionUUID: String)
      : List[ComputedGameSessionRegSP] = {

    val e = DSL.using(c, dialectToUse)

    /*val k = e.selectFrom(LIVEBOARDSNAPSHOTS)
        .where(LIVEBOARDSNAPSHOTS.)
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
    // TODO: Resource Management*/
    c.close()

    // TODO: Fill in this function, was not needed for the prototype demo; hence, left unimplemented
    List.empty

  }

  def upsert(c: Connection, liveboardSnapshotData: DBActionLiveBoardSnapshotRecord) = {

    val e = DSL.using(c, dialectToUse)

    val snapshotRecord = e.newRecord(LIVEBOARDSNAPSHOTS)

    snapshotRecord.setCompanyid               (liveboardSnapshotData.companyID)
    snapshotRecord.setBelongstodepartment     (liveboardSnapshotData.belongsToDepartment)
    snapshotRecord.setPlayerid                (liveboardSnapshotData.playerID)
    snapshotRecord.setGameid                  (liveboardSnapshotData.gameID)
    snapshotRecord.setGroupid                 (liveboardSnapshotData.groupID)
    snapshotRecord.setGametype                (liveboardSnapshotData.gameType)
    snapshotRecord.setSnapshottakenat         (Timestamp.valueOf(liveboardSnapshotData.takenAtInUTC))
    snapshotRecord.setTimezoneapplicable      (liveboardSnapshotData.timezoneApplicable)
    snapshotRecord.setRankcomputed            (liveboardSnapshotData.rankComputed)

    val result = e.insertInto(LIVEBOARDSNAPSHOTS)
       .set(snapshotRecord)
       .execute()

    // TODO: We are taking particular care to explicitly closing a connection below.
    // TODO: However, we have to find out a good functional approach to emulate Java's
    // TODO: Try-with-resources here (in Scala World, it is called ARM: Automatic
    // TODO: Resource Management
    c.close()

    result

  }

}

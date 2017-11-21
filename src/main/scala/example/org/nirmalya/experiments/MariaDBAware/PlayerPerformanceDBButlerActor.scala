package example.org.nirmalya.experiments.MariaDBAware

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.{LocalDateTime, ZoneId}

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.DBHatch.{DBActionGameSessionRecord, DBActionPlayerPerformanceRecord}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.PlayerPerformanceRecordSP
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import generated.Tables._

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

      val dbRecord = DBActionPlayerPerformanceRecord(
        r.companyID,
        r.belongsToDepartment,
        r.playerID,
        r.gameID,
        gameType="SP",
        r.lastPlayedOn.toLocalDateTime,
        r.timezoneApplicable,
        r.pointsObtained,
        r.timeTaken,
        winsAchieved = 0
      )
      val rows = PlayerPerformanceDBButlerActor
                 .upsert(
                   DriverManager.getConnection(connectionString),
                   dbRecord
                 )

      sender ! rows

    case u: DBActionPlayerToUpdate   =>

  }

}

object PlayerPerformanceDBButlerActor {

  def apply(connectionString: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionDBButlerActor(dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String)
      : List[DBActionPlayerPerformanceRecord] = {

    val e = DSL.using(c, SQLDialect.MARIADB)
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

    val e = DSL.using(c, SQLDialect.MARIADB)

    val playerPerformanceRecord = e.newRecord(PLAYERPERFORMANCE)

    playerPerformanceRecord.setCompanyid               (playerPerformanceData.companyID)
    playerPerformanceRecord.setBelongstodepartment     (playerPerformanceData.belongsToDepartment)
    playerPerformanceRecord.setPlayerid                (playerPerformanceData.playerID)
    playerPerformanceRecord.setGameid                  (playerPerformanceData.gameID)
    playerPerformanceRecord.setLastplayedon            (Timestamp.valueOf(playerPerformanceData.lastPlayedOn))
    playerPerformanceRecord.setTimezoneapplicable      (playerPerformanceData.timezoneApplicable)
    playerPerformanceRecord.setPointsobtained          (playerPerformanceData.pointsObtained)
    playerPerformanceRecord.setTimetaken               (playerPerformanceData.timeTaken)
    playerPerformanceRecord.setWinsachieved            (playerPerformanceData.winsAchieved)


    e.insertInto(PLAYERPERFORMANCE)
       .set(playerPerformanceRecord)
       .onDuplicateKeyUpdate()
       .set(playerPerformanceRecord)
       .execute()

  }

}

object NonExistentPlayerPerformance extends
  DBActionPlayerPerformanceRecord(
    "Unknown","Unknown","Unknown","Unknown","Unknown",LocalDateTime.now(ZoneId.of("Z")),"Unknown",-1,-1,-1)


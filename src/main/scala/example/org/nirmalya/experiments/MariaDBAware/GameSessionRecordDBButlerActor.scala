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


/**
  * Created by nirmalya on 4/10/17.
  */

case class DBActionGameSessionRecordInsert(
                                            companyID: String, belongsToDepartment: String, playerID: String, gameID: String,
                                            gameSessionUUID: String, companyName: String, manager: String, belongsToGroup: String, gameName: String,
                                            lastPlayedOn: LocalDateTime, timezoneApplicable: String, endReason: String, score: Int)


case class GameSessionRecord(companyID: String, belongsToDepartment: String, playerID: String, gameID: String,
                             gameSessionUUID: String, companyName: String, manager: String, belongsToGroup: String, gameName: String,
                             lastPlayedOn: LocalDateTime, timezoneApplicable: String, endReason: String, score: Int)

class GameSessionRecordButlerActor(
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

object GameSessionRecordButlerActor {

  def apply(connectionString: String, dbAccessDispatcher: ExecutionContextExecutor): Props =

             Props(new GameSessionRecordButlerActor(connectionString,dbAccessDispatcher))

  def retrieve(
        c: Connection, companyID: String, department: String, gameID: String, playerID: String, gameSessionUUID: String)
      : List[GameSessionRecord] = {

    val e = DSL.using(c, SQLDialect.MARIADB)
    val x = GAMESESSIONRECORDS as "x"

    val k = e.selectFrom(GAMESESSIONRECORDS)
      .where(GAMESESSIONRECORDS.COMPANYID.eq(companyID))
      .and(GAMESESSIONRECORDS.BELONGSTODEPARTMENT.eq(department))
      .and(GAMESESSIONRECORDS.GAMEID.eq(gameID))
      .and(GAMESESSIONRECORDS.PLAYERID.eq(playerID))
      .and(GAMESESSIONRECORDS.GAMESESSIONUUID.eq(gameSessionUUID))
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
              c: Connection, gameSessionRecordableData: DBActionGameSessionRecordInsert
      ) = {

    val e = DSL.using(c, SQLDialect.MARIADB)

    val gameSessionRecord = e.newRecord(GAMESESSIONRECORDS)

    gameSessionRecord.setCompanyid               (gameSessionRecordableData.companyID)
    gameSessionRecord.setBelongstodepartment     (gameSessionRecordableData.belongsToDepartment)
    gameSessionRecord.setPlayerid                (gameSessionRecordableData.playerID)
    gameSessionRecord.setGameid                  (gameSessionRecordableData.gameID)
    gameSessionRecord.setGamesessionuuid         (gameSessionRecordableData.gameSessionUUID)
    gameSessionRecord.setCompanyname             (gameSessionRecordableData.companyName)
    gameSessionRecord.setManager                 (gameSessionRecordableData.manager)
    gameSessionRecord.setGamename                (gameSessionRecordableData.gameName)
    gameSessionRecord.setBelongstogroup          (gameSessionRecordableData.belongsToGroup)
    gameSessionRecord.setPlayedon                (Timestamp.valueOf(gameSessionRecordableData.lastPlayedOn))
    gameSessionRecord.setTimezoneapplicable      (gameSessionRecordableData.timezoneApplicable)
    gameSessionRecord.setEndreason               (gameSessionRecordableData.endReason)
    gameSessionRecord.setScore                   (gameSessionRecordableData.score)


    e.insertInto(GAMESESSIONRECORDS)
       .set(gameSessionRecord)
       .execute()

  }

}

object NonExistentGameSessionRecord extends
  GameSessionRecord("Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Uknown",LocalDateTime.MIN,"Unknown","Unknown",-1)


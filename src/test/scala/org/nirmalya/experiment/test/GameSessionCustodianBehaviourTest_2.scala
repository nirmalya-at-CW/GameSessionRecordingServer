package org.nirmalya.experiment.test

import java.sql.DriverManager
import java.time.{Instant, ZoneId, ZoneOffset}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{EventFilter, ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import example.org.nirmalya.experiments.GameSessionCustodianActor
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.DBHatch.DBActionGameSessionRecord
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, RESPGameSessionBodyWhenSuccessful}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame.EvInitiated
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{GameSession, GameSessionEndedByTimeOut, HuddleGame, LeaderboardConsumableData, QuestionAnswerTuple}
import example.org.nirmalya.experiments.MariaDBAware.NonExistentGameSessionRecord
import generated.Tables._
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.nirmalya.experiment.test.common._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

/**
  * Created by nirmalya on 1/11/17.
  */
class GameSessionCustodianBehaviourTest_2  extends TestKit(ActorSystem(
  "ActorSystem-GameSessionCustodianBehaviourTest",
  ConfigFactory.parseString(
    """GameSession {

            redisEndPoint {

              host = "localhost"
              port = 6379
            }

            availableAt {

              host = "localhost"
              port = 9090
            }

            maxGameSessionLifetime {

              // Unit is expressed in seconds
              duration = 20
            }

            maxResponseTimeLimit {
              // Every request to GameSessionRecordingService is handled internally, by passing messages and
              // delegating responsibilities to actors. Each of these actors represents a GameSession. A request
              // delegated to such an actor, should not take more than the duration specified here.
              // This is an internally used value: should be increased only after due analysis.
              // Unit is expressed in seconds
              duration = 3
            }

            externalServices {

              // HTTP endpoints, which want to be informed when a GameSession is finished
              completionSubscribers = ["http://httpbin.org/put"]
              dbAccessURL = "jdbc:mariadb://localhost:3306/OneHuddle?user=nuovo&password=nuovo123"
            }

          }

          akka {
            # event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
            // loggers = ["akka.event.slf4j.Slf4jLogger"]
            // logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
            // loggers =  ["akka.testkit.TestEventListener"] //["akka.event.Logging$DefaultLogger"]
            loglevel = "DEBUG"
          }

          akka.actor.debug.receive = true

          akka.loggers = ["akka.testkit.TestEventListener"]
    """.stripMargin)))

  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {

  val config = ConfigFactory.load()

  val dbAccessURL = config.getConfig("GameSession.externalServices").getString("dbAccessURL")

  val (redisHost, redisPort) = (

    config.getConfig("GameSession.redisEndPoint").getString("host"),
    config.getConfig("GameSession.redisEndPoint").getInt("port")
    )

  val maxGameSessionLifetime = Duration(
    config.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
    TimeUnit.SECONDS
  )

  val dummyLeaderboardServiceEndpoint =
    config.getConfig("GameSession.externalServices").getStringList("completionSubscribers").get(0)

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,5),
    QuestionAnswerTuple(2,2,true,10,5),
    QuestionAnswerTuple(3,3,false,0,5),
    QuestionAnswerTuple(4,4,true,10,5)
  )


  "A Huddle GameSession Custodian" must {

    "correctly deal with timeout when no question answered after the first two" in {

      val gameSessionInfo = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-2",playedInTimezone = "Asia/Calcutta")

      val gameStartsAt  = System.currentTimeMillis()
      val evInitiated = EvInitiated(gameStartsAt)
      val evQuizIsFinalized =   HuddleGame.EvQuizIsFinalized(gameStartsAt+1,"Some metadata")
      val evQuestionAnswered_1 =   HuddleGame.EvQuestionAnswered(gameStartsAt+2,questionaAndAnswers(0))
      val evQuestionAnswered_2 =   HuddleGame.EvQuestionAnswered(gameStartsAt+4,questionaAndAnswers(1))


      val leaderboardInfomer = system.actorOf(DummyLeaderBoardServiceGatewayActor(dummyLeaderboardServiceEndpoint))

      val custodianActorName = s"GameSessionCustodianActor-${gameSessionInfo.gameSessionUUID}"

      val  custodian = system.actorOf(
        GameSessionCustodianActor(
          gameSessionInfo,
          redisHost,
          redisPort,
          maxGameSessionLifetime,
          leaderboardInfomer,
          ""
        ),custodianActorName)

      custodian ! evInitiated

      expectMsg(RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, "Initiated"),Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID))))

      custodian ! evQuizIsFinalized

      expectMsg(RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "Prepared")))

      custodian ! evQuestionAnswered_1

      expectMsg(RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))

      custodian ! evQuestionAnswered_2

      expectMsg(RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))



      // We are effectively whiling away time so that the GameSession times out.
      // Assumption: maximum duration before a session times out is 20 seconds, from application.conf
      expectNoMsg(maxGameSessionLifetime + Duration(2,"seconds"))

      val k = fetchGameSessionFromDB(gameSessionInfo) // 'k' is a list

      assert(
        k != NonExistentGameSessionRecord &&
          k.tail.isEmpty                    &&  // Because we expect only one record with corresponding keyfields to exist
          k.head.companyID             == gameSessionInfo.companyID            &&
          k.head.belongsToDepartment   == gameSessionInfo.departmentID         &&
          k.head.gameID                == gameSessionInfo.gameID               &&
          k.head.playerID              == gameSessionInfo.playerID             &&
          k.head.gameSessionUUID       == gameSessionInfo.gameSessionUUID      &&
          k.head.endReason             == GameSessionEndedByTimeOut.toString   &&
          k.head.score                 == (evQuestionAnswered_1.questionAndAnswer.points + evQuestionAnswered_2.questionAndAnswer.points)
      )

    }

  }

  private def
  fetchGameSessionFromDB(gameSessionInfo: GameSession) = {

    val e = DSL.using(DriverManager.getConnection(dbAccessURL), SQLDialect.MARIADB)
    val x = GAMESESSIONRECORDS as "x"

    val k = e.selectFrom(GAMESESSIONRECORDS)
          .where(GAMESESSIONRECORDS.COMPANYID.eq(gameSessionInfo.companyID))
          .and(GAMESESSIONRECORDS.BELONGSTODEPARTMENT.eq(gameSessionInfo.departmentID))
          .and(GAMESESSIONRECORDS.GAMEID.eq(gameSessionInfo.gameID))
          .and(GAMESESSIONRECORDS.PLAYERID.eq(gameSessionInfo.playerID))
          .and(GAMESESSIONRECORDS.GAMESESSIONUUID.eq(gameSessionInfo.gameSessionUUID))
          .fetchInto(classOf[DBActionGameSessionRecord])
          .asScala
          .toList

    if (k.isEmpty) List(NonExistentGameSessionRecord) else k
  }



}

package org.nirmalya.experiment.test

import java.sql.DriverManager
import java.time.{Instant, ZoneId, ZoneOffset}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.OneHuddle.GamePlaySessionService.GameSessionCustodianActor

import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, HuddleRESPGameSessionBodyWhenSuccessful}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.HuddleGame.EvInitiated
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{GameSession, GameSessionEndedByTimeOut, HuddleGame, QuestionAnswerTuple}
import com.OneHuddle.GamePlaySessionService.MariaDBAware.{GameSessionDBButlerActor, NonExistentGameSessionRecord}
import com.typesafe.config.ConfigFactory

import org.nirmalya.experiment.test.common._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import collection.JavaConverters._
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

      |       // host = "172.31.42.169"
      |       // port = 7003
            }

            availableAt {

              host = "localhost"
              port = 9090
            }

            maxGameSessionLifetime {

              // Unit is expressed in seconds
              duration = 2
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

  val testSpecificConfig = system.settings.config

  val dbAccessURL = testSpecificConfig.getConfig("GameSession.externalServices").getString("dbAccessURL")

  val (redisHost, redisPort) = (

    testSpecificConfig.getConfig("GameSession.redisEndPoint").getString("host"),
    testSpecificConfig.getConfig("GameSession.redisEndPoint").getInt("port")
    )

  val maxGameSessionLifetime = Duration(
    testSpecificConfig.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
    TimeUnit.SECONDS
  )

  val dummyLeaderboardServiceEndpoint =
    testSpecificConfig.getConfig("GameSession.externalServices").getStringList("completionSubscribers").get(0)

  val adminPanelNotifierActor = system.actorOf(DummyAdminPanelNotifierActor.apply(),"~~DummyAdminPanelNotifier~~")

  val lrsProbe = TestProbe()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,5),
    QuestionAnswerTuple(2,2,true,10,5),
    QuestionAnswerTuple(3,3,false,0,5),
    QuestionAnswerTuple(4,4,true,10,5)
  )


  "A Huddle GameSession Custodian" must {

    "correctly deal with timeout when no question answered after the first two" in {

      val gameSessionInfo = GameSession("1Huddle","Sales","G001","P001","Tic-Tac-Toe","UUID-19",playedInTimezone = "Asia/Calcutta")

      val gameStartsAt  = System.currentTimeMillis()
      val evInitiated = EvInitiated(gameStartsAt)
      val evQuizIsFinalized =   HuddleGame.EvQuizIsFinalized(gameStartsAt+1,"Some metadata")
      val evQuestionAnswered_1 =   HuddleGame.EvQuestionAnswered(gameStartsAt+2,questionaAndAnswers(0))
      val evQuestionAnswered_2 =   HuddleGame.EvQuestionAnswered(gameStartsAt+4,questionaAndAnswers(1))


      val leaderboardInfomer = system.actorOf(DummyLiveBoardServiceGatewayActor(dummyLeaderboardServiceEndpoint))

      val custodianActorName = s"Custodian-session-${gameSessionInfo.gameSessionUUID}"

      val  custodian = system.actorOf(
        GameSessionCustodianActor(
          gameSessionInfo,
          redisHost,
          redisPort,
          maxGameSessionLifetime,
          leaderboardInfomer,
          adminPanelNotifierActor,
          lrsProbe.ref,
          dbAccessURL
        ),custodianActorName)

      custodian ! evInitiated

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, "Initiated"),Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID))))

      custodian ! evQuizIsFinalized

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "Prepared")))

      custodian ! evQuestionAnswered_1

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))

      custodian ! evQuestionAnswered_2

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))



      // We are effectively whiling away time so that the GameSession times out.
      // Assumption: maximum duration before a session times out is as per application.conf
      expectNoMsg(maxGameSessionLifetime + Duration(3,"seconds"))

      val k = fetchGameSessionFromDB(gameSessionInfo) // 'k' is a list

      println(s"k.head.score [${k.head.totalPointsObtained}]")
      println(s"total correct answers [${evQuestionAnswered_1.questionAndAnswer.points + evQuestionAnswered_2.questionAndAnswer.points}]")

      assert(
        k != NonExistentGameSessionRecord &&
          k.tail.isEmpty                    &&  // Because we expect only one record with corresponding keyfields to exist
          k.head.companyID             == gameSessionInfo.companyID            &&
          k.head.departmentID          == gameSessionInfo.departmentID         &&
          k.head.gameID                == gameSessionInfo.gameID               &&
          k.head.playerID              == gameSessionInfo.playerID             &&
          k.head.gameSessionUUID       == gameSessionInfo.gameSessionUUID      &&
          k.head.endedBecauseOf        == GameSessionEndedByTimeOut.toString   &&
          k.head.totalPointsObtained   == (evQuestionAnswered_1.questionAndAnswer.points + evQuestionAnswered_2.questionAndAnswer.points)
      )

    }

  }

  private def
  fetchGameSessionFromDB(gameSessionInfo: GameSession) = {

    val c = DriverManager.getConnection(dbAccessURL)
    /*val e = DSL.using(c, SQLDialect.MARIADB)
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

    c.close
    if (k.isEmpty) List(NonExistentGameSessionRecord) else k*/

    val k = GameSessionDBButlerActor
      .retrieve(
        c,
        gameSessionInfo.companyID,
        gameSessionInfo.departmentID,
        gameSessionInfo.gameID,
        gameSessionInfo.playerID,
        gameSessionInfo.gameSessionUUID
      )

    c.close
    k
  }



}

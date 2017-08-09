package org.nirmalya.experiment

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import Matchers._
import com.redis.RedisClient
import org.json4s.native.Serialization.{read, write}
import example.org.nirmalya.experiments.{GamePlayRecorderActor, GameSessionSPOCActor}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame.{GameSessionHasStartedState, GameSessionIsContinuingState, GameSessionIsPausedState, GameSessionYetToStartState}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol._
import org.nirmalya.experiment.common.StopSystemAfterAll
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._
import akka.testkit._
import com.typesafe.config.ConfigFactory

import scala.language.postfixOps


/**
  * Created by nirmalya on 11/6/17.
  */
class GamePlayRecorderActorRedisDataIntegrityTest extends TestKit(ActorSystem("HuddleGame-system"))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {

  val config = ConfigFactory.load()

  val (redisHost, redisPort) = (

    config.getConfig("GameSession.redisEndPoint").getString("host"),
    config.getConfig("GameSession.redisEndPoint").getInt("port")
  )

  val maxGameTimeOut = Duration(
    config.getConfig("GameSession.maxGameTimeOut").getInt("duration"),
    TimeUnit.SECONDS
  )

  val redisUpdateIndicatorAwaitingTime = Duration(
    config.getConfig("GameSession.maxGameTimeOut").getInt("duration") + 10,
    TimeUnit.SECONDS
  )

  val redisClient = new RedisClient(redisHost, redisPort)

  val keyPrefix = "HuddleGame-Test-RedisData-"

  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,5),
    QuestionAnswerTuple(2,2,true,10,5),
    QuestionAnswerTuple(3,3,false,0,5),
    QuestionAnswerTuple(4,4,true,10,5)
  )

  override def beforeAll = super.beforeAll


  "A Huddle GamePlay Session" must {

    "store (Created,Initiated) tuples, in that order, in a history of a game session"  in {

      val actorName =  "RecorderActorForTest-1"

      val probe1 = TestProbe()

      val gameSession = GameSession(keyPrefix + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(
        GamePlayRecorderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          maxGameTimeOut,
          probe1.ref
        ),actorName)

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)

      expectMsg(RecordingStatus(s"sessionID($gameSession), Created."))

      gamePlayRecorderActor ! HuddleGame.EvGamePlayRecordSoFarRequired(gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>
          val sessionHistoryAsJSON = m.details
          val completeHistory = read[CompleteGamePlaySessionHistory](sessionHistoryAsJSON)
          completeHistory.elems.length should be (2)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameInitiatedTupleInREDIS]
          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameInitiatedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")
          aStartedTuple.t shouldEqual (gameStartsAt)

      }

    }

    "store (Created,Initiated,Prepared,Played,Paused,Played,Ended) tuples, in that order, in a history of a game session" in {

      val actorName =  "RecorderActorForTest-3"

      val probe1 = TestProbe()

      val gameSession = GameSession(keyPrefix + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(
        GamePlayRecorderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameTimeOut,
            probe1.ref
          ),actorName)

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Created."))

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt+1,List(1,2,3,4),gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Quiz set up (1|2|3|4)."))

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+2,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Played(Q:${questionaAndAnswers(0).questionID},A:${questionaAndAnswers(0).answerID})."))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+3,gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Paused."))


      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+4,questionaAndAnswers(1),gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Played(Q:${questionaAndAnswers(1).questionID},A:${questionaAndAnswers(1).answerID})."))


      gamePlayRecorderActor ! HuddleGame.EvEnded(
                                           gameStartsAt+5,
                                           GameSessionEndedByPlayer,
                                           5,
                                           gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>

          m.details should be (s"sessionID($gameSession), Ended.")
      }

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some (v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be (7)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameInitiatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a [GamePreparedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a [GamePlayedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a [GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(5) shouldBe a [GamePlayedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(6) shouldBe a [GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameInitiatedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aPreparedTuple1 = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePreparedTupleInREDIS]
          aPreparedTuple1.questionIDs shouldEqual List(1,2,3,4)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePlayedTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt+3)

          val aPlayTuple2 = completeHistory.elems.toIndexedSeq(5).asInstanceOf[GamePlayedTupleInREDIS]
          aPlayTuple2.questionAnswer shouldEqual (questionaAndAnswers(1))

          val anEndedTuple = completeHistory.elems.toIndexedSeq(6).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple shouldEqual (GameEndedTupleInREDIS(gameStartsAt+5, GameSessionEndedByPlayer.toString, 5 ))

      }

    }

    "ignore successive Pauses, if that occurs during a game session" in {

      val actorName =  "RecorderActorForTest-4"

      val probe1 = TestProbe()

      val gameSession = GameSession(keyPrefix + "-" + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(
        GamePlayRecorderActor(
              true,
              gameSession,
              redisHost,
              redisPort,
              maxGameTimeOut,
              probe1.ref
        ),actorName
      )

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Created."))

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt+1,List(1,2,3,4),gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Quiz set up (1|2|3|4)."))

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+2,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Played(Q:${questionaAndAnswers(0).questionID},A:${questionaAndAnswers(0).answerID})."))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+3,gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Paused."))

      // Successive Pause, will be ignored by the GameSession's FSM
      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+4,gameSession)
      expectNoMsg(2 second)

      gamePlayRecorderActor ! HuddleGame.EvEnded(gameStartsAt+5,GameSessionEndedByPlayer, 2, gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>
          m.details should be (s"sessionID($gameSession), Ended.")
      }

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some (v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be (6)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameInitiatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a [GamePreparedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a [GamePlayedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a [GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(5) shouldBe a [GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameInitiatedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aInitiatedTuple = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePreparedTupleInREDIS]
          aInitiatedTuple.t shouldEqual (gameStartsAt + 1)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePlayedTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt+3)

          val anEndedTuple = completeHistory.elems.toIndexedSeq(5).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple shouldEqual (GameEndedTupleInREDIS(gameStartsAt+5, GameSessionEndedByPlayer.toString,2))

        case None => 0 shouldEqual (1) // Here, we want to know if there is a failure!

      }
    }

    "time out and end the game forcefully, after remaining at Paused state for more than a specified duration" in {

      val actorName =  "RecorderActorForTest-5"

      val probe1 = TestProbe()

      val gameSession = GameSession(keyPrefix + "-" + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(
        GamePlayRecorderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          FiniteDuration(6,TimeUnit.SECONDS), // Overriding the maxGameTimeOut value here, because we want the testcase to finish fast
          probe1.ref
        ),actorName
      )

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Created."))

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt+1,List(1,2,3,4),gameSession)
      expectMsg(RecordingStatus(s"sessionID($gameSession), Quiz set up (1|2|3|4)."))


      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+2,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Played(Q:${questionaAndAnswers(0).questionID},A:${questionaAndAnswers(0).answerID})."))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+3,gameSession)
      expectMsg(
        RecordingStatus(
          s"sessionID($gameSession), Paused."))

      awaitCond(
        {
          // 'SessionHistory' below, is hardcoded as the 'V', in (K,V) as stored in REDIS hash.
          val history = redisClient.hget(gameSession, "SessionHistory")

          history match {
            case Some(v) =>
              val completeHistory = read[CompleteGamePlaySessionHistory](v)
              completeHistory.elems.length == 6 // Created+Initiated+Prepared+Played+Paused+Ended
            case None =>
              false
          }
        },
        redisUpdateIndicatorAwaitingTime,
        10 seconds, // We are waiting for 10 seconds, because maxGameTimeOut has been initialized as 6 Seconds, in this case
        "REDIS"
      )

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some(v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be(6)
          completeHistory.elems.toIndexedSeq(0) shouldBe a[GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a[GameInitiatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a[GamePreparedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a[GamePlayedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a[GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(5) shouldBe a[GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameInitiatedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aInitiatedTuple = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePreparedTupleInREDIS]
          aInitiatedTuple.t shouldEqual (gameStartsAt + 1)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePlayedTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt + 3)

          val anEndedTuple = completeHistory.elems.toIndexedSeq(5).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple.gameEndingReason should be(GameSessionEndedByTimeOut.toString)

        case None => 0 shouldEqual (1) // Here, we want to know if there is a failure!

      }

    }

  }
}

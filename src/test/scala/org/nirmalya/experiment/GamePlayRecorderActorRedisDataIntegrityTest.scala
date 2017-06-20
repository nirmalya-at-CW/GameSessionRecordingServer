package org.nirmalya.experiment

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import Matchers._
import com.redis.RedisClient
import org.json4s.native.Serialization.{read, write}
import org.json4s.native.Json
import example.org.nirmalya.experiments.{Fire, GamePlayRecorderActor, GameSessionSPOCActor}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame.{GameHasStartedState, GameIsContinuingState, GameIsPausedState, GameYetToStartState}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol._
import org.nirmalya.experiment.common.StopSystemAfterAll
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._
import akka.testkit._

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

  val redisClient = new RedisClient("127.0.0.1", 6379)

  val keyPrefix = "HuddleGame-Test-RedisData-"

  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10),
    QuestionAnswerTuple(2,2,true,10),
    QuestionAnswerTuple(3,3,false,0),
    QuestionAnswerTuple(4,4,true,10)
  )

  override def beforeAll = super.beforeAll


  "A Huddle GamePlay Actor" must {

    "store (Created,Started) tuples, in that order, in a history of a game session"  in {

      val actorName =  "RecorderActorForTest-1"

      val gameSession = GameSession(keyPrefix + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),actorName)

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)

      expectMsg(RecordingStatus(s"Game session ($gameSession), start recorded"))

      gamePlayRecorderActor ! HuddleGame.EvGamePlayRecordSoFarRequired(gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>
          val sessionHistoryAsJSON = m.details
          val completeHistory = read[CompleteGamePlaySessionHistory](sessionHistoryAsJSON)
          completeHistory.elems.length should be (2)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameStartedTupleInREDIS]
          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameStartedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")
          aStartedTuple.t shouldEqual (gameStartsAt)

      }

    }

    "store (Created,Started,Played,Paused,Played,Ended) tuples, in that order, in a history of a game session" in {

      val actorName =  "RecorderActorForTest-3"

      val gameSession = GameSession(keyPrefix + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),actorName)

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"Game session ($gameSession), start recorded"))

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+1,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), question(${questionaAndAnswers(0).questionID},${questionaAndAnswers(0).answerID} recorded"))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+2,gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), Pause(${gameStartsAt+2}) recorded"))


      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+3,questionaAndAnswers(1),gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), question(${questionaAndAnswers(1).questionID},${questionaAndAnswers(1).answerID} recorded"))


      gamePlayRecorderActor ! HuddleGame.EvEnded(gameStartsAt+4,GameEndedByPlayer, gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>

          println("m.details =" + m.details)
          m.details should be (s"Game session ($gameSession), End(${gameStartsAt+4}) recorded")
      }

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some (v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be (6)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameStartedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a [GamePlayTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a [GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a [GamePlayTupleInREDIS]
          completeHistory.elems.toIndexedSeq(5) shouldBe a [GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameStartedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePlayTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt+2)

          val aPlayTuple2 = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GamePlayTupleInREDIS]
          aPlayTuple2.questionAnswer shouldEqual (questionaAndAnswers(1))

          val anEndedTuple = completeHistory.elems.toIndexedSeq(5).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple shouldEqual (GameEndedTupleInREDIS(gameStartsAt+4, GameEndedByPlayer.toString))

      }

    }

    "ignore successive Pause, in a history of a game session" in {

      val actorName =  "RecorderActorForTest-4"

      val gameSession = GameSession(keyPrefix + "-" + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),actorName)

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"Game session ($gameSession), start recorded"))

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+1,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), question(${questionaAndAnswers(0).questionID},${questionaAndAnswers(0).answerID} recorded"))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+2,gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), Pause(${gameStartsAt+2}) recorded"))

      // Successive Pause, will be ignored by the GameSession's FSM
      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+3,gameSession)
      expectNoMsg(2 second)

      gamePlayRecorderActor ! HuddleGame.EvEnded(gameStartsAt+4,GameEndedByPlayer, gameSession)

      expectMsgPF(2 second) {

        case m: RecordingStatus =>
          m.details should be (s"Game session ($gameSession), End(${gameStartsAt+4}) recorded")
      }

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some (v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be (5)
          completeHistory.elems.toIndexedSeq(0) shouldBe a [GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a [GameStartedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a [GamePlayTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a [GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a [GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be ("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameStartedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePlayTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt+2)

          val anEndedTuple = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple shouldEqual (GameEndedTupleInREDIS(gameStartsAt+4, GameEndedByPlayer.toString))

        case None => 0 shouldEqual (1) // Here, we want to know if there is a failure!

      }
    }

    "time out and end the game forcefully, after remaining at Paused state for more than a specified duration" in {

      val actorName =  "RecorderActorForTest-5"

      val gameSession = GameSession(keyPrefix + "-" + actorName, "Player-01")

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),actorName)

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)
      expectMsg(RecordingStatus(s"Game session ($gameSession), start recorded"))

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt+1,questionaAndAnswers(0),gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), question(${questionaAndAnswers(0).questionID},${questionaAndAnswers(0).answerID} recorded"))


      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt+2,gameSession)
      expectMsg(
        RecordingStatus(
          s"Game session ($gameSession), Pause(${gameStartsAt+2}) recorded"))

      awaitCond(
        {
          val history = redisClient.hget(gameSession, "SessionHistory")

          history match {
            case Some(v) =>
              val completeHistory = read[CompleteGamePlaySessionHistory](v)
              completeHistory.elems.length == 5
            case None =>
              false
          }
        }, 20 seconds, 5 seconds, "REDIS"
      )

      val history = redisClient.hget(gameSession, "SessionHistory")

      history match {
        case Some(v) =>
          val completeHistory = read[CompleteGamePlaySessionHistory](v)
          completeHistory.elems.length should be(5)
          completeHistory.elems.toIndexedSeq(0) shouldBe a[GameCreatedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(1) shouldBe a[GameStartedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(2) shouldBe a[GamePlayTupleInREDIS]
          completeHistory.elems.toIndexedSeq(3) shouldBe a[GamePausedTupleInREDIS]
          completeHistory.elems.toIndexedSeq(4) shouldBe a[GameEndedTupleInREDIS]

          val aCreatedTuple = completeHistory.elems.toIndexedSeq(0).asInstanceOf[GameCreatedTupleInREDIS]
          aCreatedTuple.flag should be("Game Sentinel")

          val aStartedTuple = completeHistory.elems.toIndexedSeq(1).asInstanceOf[GameStartedTupleInREDIS]
          aStartedTuple.t shouldEqual (gameStartsAt)

          val aPlayTuple1 = completeHistory.elems.toIndexedSeq(2).asInstanceOf[GamePlayTupleInREDIS]
          aPlayTuple1.questionAnswer shouldEqual (questionaAndAnswers(0))

          val aPlayPausedTuple = completeHistory.elems.toIndexedSeq(3).asInstanceOf[GamePausedTupleInREDIS]
          aPlayPausedTuple.t shouldEqual (gameStartsAt + 2)

          val anEndedTuple = completeHistory.elems.toIndexedSeq(4).asInstanceOf[GameEndedTupleInREDIS]
          anEndedTuple.gameEndingReason should be(GameEndedByTimeOut.toString)

        case None => 0 shouldEqual (1) // Here, we want to know if there is a failure!

      }

    }

  }
}

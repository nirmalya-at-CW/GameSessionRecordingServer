package org.nirmalya.experiment

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}


import example.org.nirmalya.experiments.GamePlayRecorderActor
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{GameSession, HuddleGame, QuestionAnswerTuple}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame._
import org.nirmalya.experiment.common.StopSystemAfterAll



import scala.language.postfixOps
import scala.concurrent.duration.{Duration, _}


/**
  * Created by nirmalya on 11/6/17.
  */
class GamePlayRecorderActorStateCorrectnessTest extends TestKit(ActorSystem("HuddleGame-system"))
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

  val gameSession = GameSession("HuddleGame-Test-FSM", "Player-01")
  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
            QuestionAnswerTuple(1, 1, true, 10,5),
            QuestionAnswerTuple(2, 2, true, 10,5),
            QuestionAnswerTuple(3, 3, false, 0,5),
            QuestionAnswerTuple(4, 4, true, 10,5)
  )

  override def beforeAll = super.beforeAll

  "A Huddle GamePlay Session" must {

    "be yet to start, just after creation" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(
        GamePlayRecorderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          maxGameTimeOut,
          dummyProbe.ref
        ), "RecorderActorForTest-1")

      val testProbe = TestProbe()

      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }
    }

    "expect a quiz to arrive, after game starts" in {

      val dummyProbe = TestProbe()


      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-2")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }
    }

    "have started, after a set of questions has arrived" in {

      val dummyProbe = TestProbe()


      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-3")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 3, "some metedata", gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }
    }

    "remain at Continuing state, when the player answers a question" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-4")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 3, List(1,4,5,9).mkString("|"), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(0), gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }

    }

    "remain at Paused state, when the player pauses the game after starting the game" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-5")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 5, questionaAndAnswers(0), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 7, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsContinuingState, GameSessionIsPausedState) => true
      }
    }

    "move to Continuing state, when the player answers after having paused a game" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-6")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt + 2, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }


      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 4, List(1,4,5,9).mkString("|"), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 3, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsPausedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(3), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsPausedState, GameSessionIsContinuingState) => true
      }

    }

    "move to CONTINUING state, when the player plays a clip after starting the game" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-7")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"), gameSession)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvPlayingClip(gameStartsAt + 4, "sample.mp3", gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }

    }

    "remain in CONTINUING state, when the player plays a clip after already being in CONTINUING state" in {

          val dummyProbe = TestProbe()

          val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameTimeOut,
            dummyProbe.ref
          ), "RecorderActorForTest-8")

          val testProbe = TestProbe()
          gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
          testProbe.expectMsgPF() {
            case CurrentState(_, GameSessionYetToStartState) => true
          }

          gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
          }

          gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"), gameSession)
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
          }

          gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 4, questionaAndAnswers(0), gameSession)
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
          }

          gamePlayRecorderActor ! HuddleGame.EvPlayingClip(gameStartsAt + 5, "sample.mp3", gameSession)
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionIsContinuingState, GameSessionIsContinuingState) => true
          }

    }

    "time out and end the game forcefully, when the player answers one question and then doesn't any more" in {

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(
        true,
        gameSession,
        redisHost,
        redisPort,
        maxGameTimeOut,
        dummyProbe.ref
      ), "RecorderActorForTest-9")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF(1 second) {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt, gameSession)
      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"), gameSession)
      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 4, gameSession)
      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(3), gameSession)

      testProbe.expectMsgAllOf(
        25 seconds, // Assuming the timeout duration for a game is 20 seconds!
        Transition(gamePlayRecorderActor, GameSessionYetToStartState, GameSessionIsBeingPreparedState),
        Transition(gamePlayRecorderActor, GameSessionIsBeingPreparedState, GameSessionHasStartedState),
        Transition(gamePlayRecorderActor, GameSessionHasStartedState, GameSessionIsPausedState),
        Transition(gamePlayRecorderActor, GameSessionIsPausedState, GameSessionIsContinuingState),
        Transition(gamePlayRecorderActor, GameSessionIsContinuingState, GameSessionIsWrappingUpState)
      )
    }

  }

}

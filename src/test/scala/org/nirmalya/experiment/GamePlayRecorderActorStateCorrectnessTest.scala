package org.nirmalya.experiment

import java.util.UUID

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import example.org.nirmalya.experiments.GamePlayRecorderActor
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{GameSession, HuddleGame, QuestionAnswerTuple}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame._
import org.nirmalya.experiment.common.StopSystemAfterAll
import org.scalatest.time.Second
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.language.postfixOps
import scala.concurrent.duration._


/**
  * Created by nirmalya on 11/6/17.
  */
class GamePlayRecorderActorStateCorrectnessTest extends TestKit(ActorSystem("HuddleGame-system"))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {

  //val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest")

  val gameSession = GameSession("HuddleGame-Test-FSM", "Player-01")
  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10),
    QuestionAnswerTuple(2,2,true,10),
    QuestionAnswerTuple(3,3,false,0),
    QuestionAnswerTuple(4,4,true,10)
  )

  override def beforeAll = super.beforeAll

  "A Huddle GamePlay Actor" must {

     "be at state when game is yet to start" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-1")

      val testProbe = TestProbe()

      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStartState) => true
      }
    }

    "go to STARTed state, after game starts" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-2")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStartState, GameHasStartedState) => true
      }
    }

    "remain at Continuing state, when the player answers a question" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-3")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvStarted(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStartState, GameHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 2, questionaAndAnswers(0), gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStartedState, GameIsContinuingState) => true
      }

    }

    "remain at Paused state, when the player pauses the game after starting the game" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-4")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStartState) => true
      }

      gamePlayRecorderActor  ! HuddleGame.EvStarted(gameStartsAt,gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStartState, GameHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 2, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStartedState, GameIsPausedState) => true
      }
    }

    "move to Continuing state, when the player answers after having paused a game" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-5")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStartState) => true
      }

      gamePlayRecorderActor  ! HuddleGame.EvStarted(gameStartsAt,gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStartState, GameHasStartedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 2, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStartedState, GameIsPausedState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 4, questionaAndAnswers(3), gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameIsPausedState, GameIsContinuingState) => true
      }

    }
  }

  "time out and end the game forcefully, when the player answers one question and then doesn't any more" in {

    val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor(true, gameSession),"RecorderActorForTest-6")

    val testProbe = TestProbe()
    gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
    testProbe.expectMsgPF(1 second) {
      case CurrentState(_, GameYetToStartState) => true
    }

    gamePlayRecorderActor  ! HuddleGame.EvStarted(gameStartsAt,gameSession)
    gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 2, gameSession)
    gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 4, questionaAndAnswers(3), gameSession)

    testProbe.expectMsgAllOf(
         6 seconds, // Assuming the timeout duration for a game is 5 seconds!
         Transition(gamePlayRecorderActor, GameYetToStartState,   GameHasStartedState),
         Transition(gamePlayRecorderActor, GameHasStartedState,   GameIsPausedState),
         Transition(gamePlayRecorderActor, GameIsPausedState,     GameIsContinuingState),
         Transition(gamePlayRecorderActor, GameIsContinuingState, GameIsWrappingUpState)
    )
  }

}

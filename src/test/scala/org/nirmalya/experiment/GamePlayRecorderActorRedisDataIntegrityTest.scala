package org.nirmalya.experiment

import java.util.UUID

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import example.org.nirmalya.experiments.GamePlayRecorderActor
import example.org.nirmalya.experiments.protocol.{GameSession, HuddleGame, QuestionAnswerTuple}
import example.org.nirmalya.experiments.protocol.HuddleGame.{GameHasStarted, GameIsContinuing, GameIsPaused, GameYetToStart}
import org.nirmalya.experiment.common.StopSystemAfterAll
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.language.postfixOps
import scala.concurrent.duration._


/**
  * Created by nirmalya on 11/6/17.
  */
class GamePlayRecorderActorTest extends TestKit(ActorSystem("HuddleGame-system"))
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

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest-1")

      val testProbe = TestProbe()

      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStart) => true
      }
    }

    "go to STARTed state, after game starts" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest-2")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStart) => true
      }

      gamePlayRecorderActor ! HuddleGame.Started(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStart, GameHasStarted) => true
      }
    }

    "remain at Continuing state, when the player answers a question" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest-3")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStart) => true
      }

      gamePlayRecorderActor ! HuddleGame.Started(gameStartsAt, gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStart, GameHasStarted) => true
      }

      gamePlayRecorderActor ! HuddleGame.QuestionAnswered(gameStartsAt + 2, questionaAndAnswers(0), gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStarted, GameIsContinuing) => true
      }

    }

    "remain at Paused state, when the player pauses the game after starting the game" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest-4")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStart) => true
      }

      gamePlayRecorderActor  ! HuddleGame.Started(gameStartsAt,gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStart, GameHasStarted) => true
      }

      gamePlayRecorderActor ! HuddleGame.Paused(gameStartsAt + 2, "Paused By Player", gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStarted, GameIsPaused) => true
      }
    }

    "move to Continuing state, when the player answers after having paused a game" in {

      val gamePlayRecorderActor = system.actorOf(GamePlayRecorderActor.props,"RecorderActorForTest-5")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameYetToStart) => true
      }

      gamePlayRecorderActor  ! HuddleGame.Started(gameStartsAt,gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameYetToStart, GameHasStarted) => true
      }

      gamePlayRecorderActor ! HuddleGame.Paused(gameStartsAt + 2, "Paused By Player", gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameHasStarted, GameIsPaused) => true
      }

      gamePlayRecorderActor ! HuddleGame.QuestionAnswered(gameStartsAt + 4, questionaAndAnswers(3), gameSession)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameIsPaused, GameIsContinuing) => true
      }

    }
  }
}

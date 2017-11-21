package org.nirmalya.experiment.test

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, RESPGameSessionBodyWhenSuccessful}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import example.org.nirmalya.experiments.GameSessionStateHolderActor
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{GameSession, GameSessionEndedByManager, HuddleGame, QuestionAnswerTuple}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame._
import org.nirmalya.experiment.test.common.StopSystemAfterAll

import scala.language.postfixOps
import scala.concurrent.duration.{Duration, _}


/**
  * Created by nirmalya on 11/6/17.
  */
class GameSessionStateHolderCorrectnessTest extends TestKit(ActorSystem("HuddleGame-system"))
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

  val maxGameSessionLifetime = Duration(
    config.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
    TimeUnit.SECONDS
  )


  val questionaAndAnswers = IndexedSeq(
            QuestionAnswerTuple(1, 1, true, 10,5),
            QuestionAnswerTuple(2, 2, true, 10,5),
            QuestionAnswerTuple(3, 3, false, 0,5),
            QuestionAnswerTuple(4, 4, true, 10,5)
  )

  override def beforeAll = super.beforeAll

  "A Huddle GamePlay Session" must {

    "be yet to start, just after creation" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-1",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val dummyProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          maxGameSessionLifetime
        ), "RecorderActorForTest-1")

      val testProbe = TestProbe()

      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }
    }

    "expect a quiz to arrive, after game starts" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-2",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()


      val dummyProbe = TestProbe()


      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-2")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      expectMsg(Duration(2,"second"), "Initiated")
    }
    
     "have started, after a set of questions has arrived" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-3",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()


      val dummyProbe = TestProbe()


      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-3")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }

      expectMsg("Initiated")

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 3, "some metedata")

      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }

      expectMsg("Prepared")
    }

     "remain at Continuing state, when the player answers a question" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-4",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-4")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)

      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }
      expectMsg("Initiated")

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 3, List(1,4,5,9).mkString("|"))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }
      expectMsg("Prepared")

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(0))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }
      expectMsg("QuestionAnswered")

    }

     "remain at Paused state, when the player pauses the game after starting the game" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-6",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-5")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }
      expectMsg("Initiated")

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }
      expectMsg("Prepared")

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 5, questionaAndAnswers(0))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }
      expectMsg("QuestionAnswered")

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 7)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsContinuingState, GameSessionIsPausedState) => true
      }
      expectMsg("Paused")
    }

     "move to Continuing state, when the player answers after having paused a game" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-7",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-6")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }


      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt + 2)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }
      expectMsg("Initiated")


      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 4, List(1,4,5,9).mkString("|"))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }
      expectMsg("Prepared")

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 3)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsPausedState) => true
      }
      expectMsg("Paused")

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(3))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsPausedState, GameSessionIsContinuingState) => true
      }
      expectMsg("QuestionAnswered")

    }

     "move to Continuing state, when the player plays a clip after starting the game" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-8",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-7")

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF() {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
      }
      expectMsg("Initiated")

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
      }
      expectMsg("Prepared")

      gamePlayRecorderActor ! HuddleGame.EvPlayingClip(gameStartsAt + 4, "sample.mp3")
      testProbe.expectMsgPF(2 second) {
        case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
      }
      expectMsg("Clip Played")

    }

     "remain in Continuing state, when the player plays a clip after already being in Continuing state" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-9",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

          val gamePlayRecorderActor = system.actorOf(GameSessionStateHolderActor(
                true,
                gameSession,
                redisHost,
                redisPort,
                maxGameSessionLifetime
          ), "RecorderActorForTest-8")

          val testProbe = TestProbe()
          gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
          testProbe.expectMsgPF() {
            case CurrentState(_, GameSessionYetToStartState) => true
          }

          gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionYetToStartState, GameSessionIsBeingPreparedState) => true
          }
          expectMsg("Initiated")

          gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionIsBeingPreparedState, GameSessionHasStartedState) => true
          }
          expectMsg("Prepared")

          gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 4, questionaAndAnswers(0))
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionHasStartedState, GameSessionIsContinuingState) => true
          }
          expectMsg("QuestionAnswered")

          gamePlayRecorderActor ! HuddleGame.EvPlayingClip(gameStartsAt + 5, "sample.mp3")
          testProbe.expectMsgPF(2 second) {
            case Transition(_, GameSessionIsContinuingState, GameSessionIsContinuingState) => true
          }
          expectMsg("Clip Played")

    }

    "time out and end the game forcefully, when the " +
      "player answers one question and then doesn't any more" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-10",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val stateholderWatcherProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          maxGameSessionLifetime
      ), "RecorderActorForTest-9")

      stateholderWatcherProbe.watch(gamePlayRecorderActor)

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF(1 second) {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      expectMsg("Initiated")
      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
      expectMsg("Prepared")
      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 4)
      expectMsg("Paused")
      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(0))
      expectMsg("QuestionAnswered")

      expectNoMsg(maxGameSessionLifetime + Duration(2, "second"))
      gamePlayRecorderActor ! EvGameSessionTerminationIndicated

      testProbe.expectMsgAllOf(
        //maxGameSessionLifetime + Duration(2, "second"),
        Transition(gamePlayRecorderActor, GameSessionYetToStartState,       GameSessionIsBeingPreparedState),
        Transition(gamePlayRecorderActor, GameSessionIsBeingPreparedState,  GameSessionHasStartedState),
        Transition(gamePlayRecorderActor, GameSessionHasStartedState,       GameSessionIsPausedState),
        Transition(gamePlayRecorderActor, GameSessionIsPausedState,         GameSessionIsContinuingState),
        Transition(gamePlayRecorderActor, GameSessionIsContinuingState,     GameSessionIsWrappingUpState),
        Transition(gamePlayRecorderActor, GameSessionIsWrappingUpState,     GameSessionIsWaitingForInstructionToClose)
      )

      stateholderWatcherProbe.expectTerminated(gamePlayRecorderActor, Duration(2, "second"))
    }

     "end the game forcefully, when the Manager wants it to" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-11",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

       val stateholderWatcherProbe = TestProbe()

      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameSessionLifetime
      ), "RecorderActorForTest-10")

      stateholderWatcherProbe.watch(gamePlayRecorderActor)

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF(1 second) {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 4)
      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(3))
      gamePlayRecorderActor ! HuddleGame.EvForceEndedByManager(gameStartsAt + 8, "Vikas")

      expectMsgAllOf("Initiated","Prepared","Paused","QuestionAnswered","Ended")

      expectNoMsg(Duration(5, "second"))
      gamePlayRecorderActor ! EvGameSessionTerminationIndicated

      testProbe.expectMsgAllOf(
        maxGameSessionLifetime + Duration(2, "second"),
        Transition(gamePlayRecorderActor, GameSessionYetToStartState,       GameSessionIsBeingPreparedState),
        Transition(gamePlayRecorderActor, GameSessionIsBeingPreparedState,  GameSessionHasStartedState),
        Transition(gamePlayRecorderActor, GameSessionHasStartedState,       GameSessionIsPausedState),
        Transition(gamePlayRecorderActor, GameSessionIsPausedState,         GameSessionIsContinuingState),
        Transition(gamePlayRecorderActor, GameSessionIsContinuingState,     GameSessionIsWrappingUpState),
        Transition(gamePlayRecorderActor, GameSessionIsWrappingUpState,     GameSessionIsWaitingForInstructionToClose)
      )

      stateholderWatcherProbe.expectTerminated(gamePlayRecorderActor, Duration(2,"second"))

    }

    "end the game, when the Player leaves the game" in {

      val gameSession = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-12",playedInTimezone = "Asia/Calcutta")
      val gameStartsAt = System.currentTimeMillis()

      val stateholderWatcherProbe = TestProbe()
      val gamePlayRecorderActor = system.actorOf(
        GameSessionStateHolderActor(
          true,
          gameSession,
          redisHost,
          redisPort,
          maxGameSessionLifetime
        ), "RecorderActorForTest-11")

      stateholderWatcherProbe.watch(gamePlayRecorderActor)

      val testProbe = TestProbe()
      gamePlayRecorderActor ! SubscribeTransitionCallBack(testProbe.ref)
      testProbe.expectMsgPF(1 second) {
        case CurrentState(_, GameSessionYetToStartState) => true
      }

      gamePlayRecorderActor ! HuddleGame.EvInitiated(gameStartsAt)
      expectMsg("Initiated")

      gamePlayRecorderActor ! HuddleGame.EvQuizIsFinalized(gameStartsAt + 2, List(1,4,5,9).mkString("|"))
      expectMsg("Prepared")

      gamePlayRecorderActor ! HuddleGame.EvPaused(gameStartsAt + 4)
      expectMsg("Paused")

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 6, questionaAndAnswers(2))
      expectMsg("QuestionAnswered")

      gamePlayRecorderActor ! HuddleGame.EvQuestionAnswered(gameStartsAt + 8, questionaAndAnswers(3))
      expectMsg("QuestionAnswered")

      gamePlayRecorderActor ! HuddleGame.EvEndedByPlayer(gameStartsAt + 10, 10)
      expectMsg("Ended")

      testProbe.expectMsgAllOf(
        2 seconds,
        Transition(gamePlayRecorderActor, GameSessionYetToStartState,       GameSessionIsBeingPreparedState),
        Transition(gamePlayRecorderActor, GameSessionIsBeingPreparedState,  GameSessionHasStartedState),
        Transition(gamePlayRecorderActor, GameSessionHasStartedState,       GameSessionIsPausedState),
        Transition(gamePlayRecorderActor, GameSessionIsPausedState,         GameSessionIsContinuingState),
        Transition(gamePlayRecorderActor, GameSessionIsContinuingState,     GameSessionIsWrappingUpState)
      )

      gamePlayRecorderActor ! EvGameSessionTerminationIndicated
      expectNoMsg(maxGameSessionLifetime +Duration(2, "second"))
      stateholderWatcherProbe.expectTerminated(gamePlayRecorderActor, Duration(2,"second"))
    }

  }

}

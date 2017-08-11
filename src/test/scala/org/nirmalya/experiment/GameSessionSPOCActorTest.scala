package org.nirmalya.experiment

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{REQPauseAGameWith, REQPlayAGameWith, REQSetQuizForGameWith, REQStartAGameWith}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{GameSession, QuestionAnswerTuple, RecordingStatus}
import example.org.nirmalya.experiments.{GameSessionCompletionEmitterActor, GameSessionSPOCActor}
import org.nirmalya.experiment.common.StopSystemAfterAll
import org.scalatest.time.Seconds
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by nirmalya on 22/6/17.
  */
class GameSessionSPOCActorTest extends TestKit(ActorSystem("HuddleGame-system"))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {

  val inCorrectGameSession = GameSession("HuddleGame-Test-SPOC-NonExistent", "Player-01")
  val (company,manager,player,gamename,uuid) = ("Codewalla","Boss","minion","tic-tac-toe","A123")
  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,2),
    QuestionAnswerTuple(2,2,true,10,2),
    QuestionAnswerTuple(3,3,false,0,3),
    QuestionAnswerTuple(4,4,true,10,1)
  )

  val emitterActor = system.actorOf(GameSessionCompletionEmitterActor(List("http://httpbin.org/put")))

  override def beforeAll = super.beforeAll

  "A Huddle GamePlay SPOC Actor" must {

    "indicate that a GamePlayRecorder Actor doesn't exist for a wrong session id" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))
      spocActor ! REQPlayAGameWith(
                      inCorrectGameSession.toString,
                      questionaAndAnswers(0).questionID.toString,
                      questionaAndAnswers(0).answerID.toString,
                      questionaAndAnswers(0).isCorrect,
                      questionaAndAnswers(0).points,
                      questionaAndAnswers(0).timeTakenToAnswerAtFE
                  )

      expectMsg(RecordingStatus(s"No session with ${inCorrectGameSession.toString} exists."))
    }

    "confirm that a GamePlayRecorder Actor has started" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val req = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A123")

      spocActor ! req

      expectMsg(RecordingStatus(s"sessionID(${req.toString}), Created."))
    }

    "confirm that a GamePlayRecorder Actor that has already started, has paused correctly" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val reqStart = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A1234")

      spocActor ! reqStart

      expectMsg(RecordingStatus(s"sessionID(${reqStart.toString}), Created."))

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.toString,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(RecordingStatus(s"sessionID(${reqStart.toString}), Quiz set up (1|2|3|4)."))

      val reqPause = REQPauseAGameWith(reqStart.toString)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:RecordingStatus =>
          m.details.contains("Game session (${reqStart.toString})") &&
          m.details.contains("Pause") &&
          m.details.contains("recorded")
      }
    }

    "confirms that a GamePlayRecorder Actor, already started, is stopped automatically after inaction of certain time" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val reqStart = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A12345")

      spocActor ! reqStart

      expectMsg(RecordingStatus(s"sessionID(${reqStart.toString}), Created."))

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.toString,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(RecordingStatus(s"sessionID(${reqStart.toString}), Quiz set up (1|2|3|4)."))

      val reqPause = REQPauseAGameWith(reqStart.toString)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:RecordingStatus =>
          m.details.contains("Game session (${reqStart.toString})") &&
            m.details.contains("Pause") &&
            m.details.contains("recorded")
      }

      val reqPlay =  REQPlayAGameWith(
                            reqStart.toString,
                            questionaAndAnswers(0).questionID.toString,
                            questionaAndAnswers(0).answerID.toString,
                            questionaAndAnswers(0).isCorrect,
                            questionaAndAnswers(0).points,
                            questionaAndAnswers(0).timeTakenToAnswerAtFE
      )

      spocActor ! reqPlay

      expectMsgPF (Duration(3, "second")) {
        case m:RecordingStatus =>
          m.details == (s"Game session ($reqStart.toString), question(${questionaAndAnswers(0).questionID},${questionaAndAnswers(0).answerID} recorded")
      }

      // 10 Seconds is hardcoded at the moment, as the duration for timeout in GamePlayRecorder's FSM
      expectNoMsg(Duration(10, "second"))

      val reqPlayAgain =  REQPlayAGameWith(
        reqStart.toString,
        questionaAndAnswers(1).questionID.toString,
        questionaAndAnswers(1).answerID.toString,
        questionaAndAnswers(1).isCorrect,
        questionaAndAnswers(1).points,
        2
      )

      spocActor ! reqPlayAgain

      expectMsgPF (Duration(3, "second")) {
        case m:RecordingStatus =>
          m.details == (s"No session with ${reqStart.toString} exists.")
      }

    }
  }



}

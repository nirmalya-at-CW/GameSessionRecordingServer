package org.nirmalya.experiment

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams._
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

  val inCorrectGameSession = GameSession("HuddleGame-Test-SPOC-NonExistent")
  //val (company,manager,player,gamename,uuid) = ("Codewalla","Boss","minion","tic-tac-toe","A123")
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

      expectMsg(
        RESPGameSessionBody(
          false,
          ExpandedMessage(1300,s"No gameSession (${inCorrectGameSession}) exists"))
      )
    }

    "confirm that a GamePlayRecorder Actor has initiated" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val req = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A123")

      spocActor ! req

      expectMsg(
        RESPGameSessionBody(
                    true,
                    ExpandedMessage(2100,"Initiated"),
                    Some(Map("gameSessionID" -> req.toString))
        )
      )
    }

    "confirm that a GamePlayRecorder Actor that has already started, has paused correctly" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val reqStart = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A1234")

      spocActor ! reqStart

      expectMsg(
        RESPGameSessionBody(
          true,
          ExpandedMessage(2100,"Initiated"),
          Some(Map("gameSessionID" -> reqStart.toString))
        )
      )

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.toString,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(
        RESPGameSessionBody(
          true,
          ExpandedMessage(2200,"Prepared")
        )
      )

      val reqPause = REQPauseAGameWith(reqStart.toString)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:RESPGameSessionBody =>
          m.opSuccess == true &&
          m.message.successId == 2200 &&
          m.message.description ==  ("Paused") &&
          m.contents == None
      }
    }

    "confirms that a GamePlayRecorder Actor, already started, is stopped automatically after inaction of certain time" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val reqStart = REQStartAGameWith("Codewalla","1","Boss","minion","1","tic-tac-toe","A12345")

      spocActor ! reqStart

      expectMsg(
        RESPGameSessionBody(
          true,
          ExpandedMessage(2100,"Initiated"),
          Some(Map("gameSessionID" -> reqStart.toString))
        )
      )

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.toString,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(
        RESPGameSessionBody(
          true,
          ExpandedMessage(2200,"Prepared")
        )
      )

      val reqPause = REQPauseAGameWith(reqStart.toString)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:RESPGameSessionBody =>
          m.opSuccess == true &&
            m.message.successId == 2200 &&
            m.message.description ==  ("Paused") &&
            m.contents == None
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
        case m:RESPGameSessionBody =>
          m.opSuccess == true &&
            m.message.successId == 2200 &&
            m.message.description ==  ("QuestionAnswered") &&
            m.contents == None
      }

      // 10 Seconds is hardcoded at the moment, as the duration for timeout in GamePlayRecorder's FSM
      // We expect the GameSession to have finished by the time we send a request again later.
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
        case m:RESPGameSessionBody =>
          m.opSuccess == false &&
            m.message.successId == 1300 &&
            m.message.description ==  (s"No gameSession (${reqStart.toString}) exists")
      }

    }
  }



}

package org.nirmalya.experiment.test

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams._
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{GameSession, QuestionAnswerTuple}
import com.OneHuddle.GamePlaySessionService.GameSessionSPOCActor
import org.nirmalya.experiment.test.common.{DummyLeaderBoardServiceGatewayActor, StopSystemAfterAll}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.TimeoutException
import scala.concurrent.duration.{Duration, _}

/**
  * Created by nirmalya on 22/6/17.
  */
class GameSessionSPOCActorTest extends TestKit(ActorSystem("HuddleGame-system"))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {

  val invalidGameSession = GameSession(
    "Unknown Company",  "Unknown Department",  "Unknown GameID",
    "Unknown PlayerID", "Unknown GameName",    "Invalid GameSessionID",
    "Unknown GroupID",  "Unknown GameType",    "Unknown Timezone"
  )

  val gameStartsAt = System.currentTimeMillis()

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,2),
    QuestionAnswerTuple(2,2,true,10,2),
    QuestionAnswerTuple(3,3,false,0,3),
    QuestionAnswerTuple(4,4,true,10,1)
  )

  val config = ConfigFactory.load()

  val maxGameSessionLifetime = Duration(
    config.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
    TimeUnit.SECONDS
  )


  // val emitterActor = system.actorOf(GameSessionCompletionEmitterActor(List("http://httpbin.org/put")))

  val dummyLeaderboardServiceEndpoint =
    config.getConfig("GameSession.externalServices").getStringList("completionSubscribers").get(0)


  val leaderboardInfomer = system.actorOf(DummyLeaderBoardServiceGatewayActor(dummyLeaderboardServiceEndpoint))

  val emitterActor = leaderboardInfomer

  override def beforeAll = super.beforeAll

  "A Huddle GamePlay SPOC Actor" must {

    "indicate that a GamePlaySession Actor doesn't exist for a wrong session id" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor),"SPOCtActorForTest-1")
      spocActor ! REQPlayAGameWith(
                      invalidGameSession.toString,
                      questionaAndAnswers(0).questionID.toString,
                      questionaAndAnswers(0).answerID.toString,
                      questionaAndAnswers(0).isCorrect,
                      questionaAndAnswers(0).points,
                      questionaAndAnswers(0).timeTakenToAnswerAtFE
                  )

      expectMsg(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${invalidGameSession.toString}) exists")))
    }

    "confirm that a GamePlaySession Actor has initiated" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor))

      val req = REQStartAGameWith("C01","QA","G02","P01","SP",groupID = None,"tic-tac-toe","UUID-10",playedInTimezone = "Asia/Calcutta")

      spocActor ! req

      expectMsg(
        HuddleRESPGameSessionBodyWhenSuccessful(
                    ExpandedMessage(2100,"Initiated"),
                    Some(Map("gameSessionID" -> req.gameSessionUUID))
        )
      )
    }

    "confirm that a GamePlaySession Actor that has already started, has paused correctly" in {


      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor),"SPOCtActorForTest-2")

      val reqStart = REQStartAGameWith("C01","QA","G02","P01","SP",groupID = None,"tic-tac-toe","UUID-11",playedInTimezone = "Asia/Calcutta")

      spocActor ! reqStart

      expectMsg(
        HuddleRESPGameSessionBodyWhenSuccessful(
          ExpandedMessage(2100,"Initiated"),
          Some(Map("gameSessionID" -> reqStart.gameSessionUUID))
        )
      )

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.gameSessionUUID,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200,"Prepared")))

      val reqPause = REQPauseAGameWith(reqStart.gameSessionUUID)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:HuddleRESPGameSessionBodyWhenSuccessful =>
          m.opSuccess == true &&
          m.message.successId == 2200 &&
          m.message.description ==  ("Paused") &&
          m.contents == None
      }
    }

    "stop GameSession Actor and itself, automatically after playing and then remaining inactive for certain time" in {

      val spocActor = system.actorOf(GameSessionSPOCActor(emitterActor),"SPOCtActorForTest-3")

      val testProbe = TestProbe()
      testProbe watch spocActor

      val reqStart = REQStartAGameWith("C01","QA","G02","P01","SP",groupID = None,"tic-tac-toe","UUID-12",playedInTimezone = "Asia/Calcutta")

      spocActor ! reqStart

      expectMsg(
        HuddleRESPGameSessionBodyWhenSuccessful(
          ExpandedMessage(2100,"Initiated"),
          Some(Map("gameSessionID" -> reqStart.gameSessionUUID))
        )
      )

      val reqQuizSetup = REQSetQuizForGameWith(reqStart.gameSessionUUID,List(1,2,3,4).mkString("|"))

      spocActor ! reqQuizSetup

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200,"Prepared")))

      val reqPause = REQPauseAGameWith(reqStart.gameSessionUUID)

      spocActor ! reqPause

      expectMsgPF (Duration(3, "second")) {
        case m:HuddleRESPGameSessionBodyWhenSuccessful =>
          m.opSuccess == true &&
            m.message.successId == 2200 &&
            m.message.description ==  ("Paused") &&
            m.contents == None
      }

      val reqPlay =  REQPlayAGameWith(
                            reqStart.gameSessionUUID,
                            questionaAndAnswers(0).questionID.toString,
                            questionaAndAnswers(0).answerID.toString,
                            questionaAndAnswers(0).isCorrect,
                            questionaAndAnswers(0).points,
                            questionaAndAnswers(0).timeTakenToAnswerAtFE
      )

      spocActor ! reqPlay

      expectMsgPF (Duration(3, "second")) {
        case m:HuddleRESPGameSessionBodyWhenSuccessful =>
          m.opSuccess == true &&
            m.message.successId == 2200 &&
            m.message.description ==  ("QuestionAnswered") &&
            m.contents == None
      }

      // We expect the GameSession to have finished by the time we send a request again later.
      expectNoMsg(maxGameSessionLifetime + Duration(2, "second"))

      val reqPlayAgain =  REQPlayAGameWith(
        reqStart.gameSessionUUID,
        questionaAndAnswers(1).questionID.toString,
        questionaAndAnswers(1).answerID.toString,
        questionaAndAnswers(1).isCorrect,
        questionaAndAnswers(1).points,
        2
      )


      spocActor ! reqPlayAgain


      // We expect that because the session must have timed-out and hence, terminated, the SPOC Actor will not find the
      // session alive. Therefore, it should immediately respond to indicate, the session's absence

      expectMsgPF(Duration(1, "second")) {

        case m: HuddleRESPGameSessionBodyWhenFailed =>
          m.opSuccess == true               &&
          m.message.successId     == 1300   &&
          m.message.description   == s"No gameSession (${reqStart.gameSessionUUID}) exists" &&
          m.contents              == None
      }
    }
  }
}

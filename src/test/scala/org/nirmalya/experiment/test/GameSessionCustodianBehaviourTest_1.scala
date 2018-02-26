package org.nirmalya.experiment.test

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.{Instant, ZoneId, ZoneOffset}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{EventFilter, ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import com.OneHuddle.GamePlaySessionService.GameSessionCustodianActor

import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, HuddleRESPGameSessionBodyWhenSuccessful}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{ComputedGameSessionRegSP, EmittedWhenGameSessionIsFinished, GameSession, GameSessionEndedByPlayer, GameSessionEndedByTimeOut, HuddleGame, LeaderBoardConsumableData, QuestionAnswerTuple}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.HuddleGame.EvInitiated
import com.OneHuddle.GamePlaySessionService.MariaDBAware.{NonExistentComputedGameSession, NonExistentGameSessionRecord}



import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.nirmalya.experiment.test.common._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by nirmalya on 1/11/17.
  */
class GameSessionCustodianBehaviourTest_1  extends TestKit(ActorSystem("ActorSystem-GameSessionCustodianBehaviourTest"))

  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with StopSystemAfterAll {


  val config = ConfigFactory.load()

  //val config = ConfigFactory.load(combinedConfig)

  val dbAccessURL = config.getConfig("GameSession.externalServices").getString("dbAccessURL")

  val (redisHost, redisPort) = (

    config.getConfig("GameSession.redisEndPoint").getString("host"),
    config.getConfig("GameSession.redisEndPoint").getInt("port")
    )

  val maxGameSessionLifetime = Duration(
    config.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
    TimeUnit.SECONDS
  )

  val dummyLiveboardServiceEndpoint =
    config.getConfig("GameSession.externalServices").getStringList("completionSubscribers").get(0)

  val questionaAndAnswers = IndexedSeq(
    QuestionAnswerTuple(1,1,true,10,5),
    QuestionAnswerTuple(2,2,true,10,5),
    QuestionAnswerTuple(3,3,false,0,5),
    QuestionAnswerTuple(4,4,true,10,5)
  )

  val adminPanelNotifierActor = system.actorOf(DummyAdminPanelNotifierActor.apply(),"DummyAdminPanelNotifier")

  val lrsProbe = TestProbe()



  "A Huddle GameSession Custodian" must {

    "respond with success on initiation of a GameSession" in {

       val gameSessionInfo = GameSession("CW","QA","G01","P01","Tic-Tac-Toe","UUID-1",playedInTimezone = "Asia/Calcutta")
       val liveboardInfomer = system.actorOf(DummyLiveBoardServiceGatewayActor(dummyLiveboardServiceEndpoint),"DummyLiveBoardServiceGatewayActor-UUID-1")


      val custodianActorName = s"GameSessionCustodianActor-${gameSessionInfo.gameSessionUUID}"

       val  custodian = system.actorOf(
         GameSessionCustodianActor(
           gameSessionInfo,
           redisHost,
           redisPort,
           maxGameSessionLifetime,
           liveboardInfomer,
           adminPanelNotifierActor,
           lrsProbe.ref,
           ""
         ),custodianActorName)
       val evinitiated = EvInitiated(System.currentTimeMillis())

      custodian ! evinitiated

      expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, "Initiated"),Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID))))

    }
  }

  "should ensure that a Liveboard Informer actor receives the correct message when the player ends a session" in {

    val timezoneApplicableForTestcase = "Asia/Calcutta"
    val gameSessionInfo = GameSession("CW", "QA", "G02", "P02", "Tic-Tac-Toe", "UUID-4", gameType = "SP", playedInTimezone = timezoneApplicableForTestcase)

    val gameStartsAt = System.currentTimeMillis()
    val instantOfStart = (Instant.ofEpochMilli(System.currentTimeMillis()))
    val zoneToUseCal = ZoneId.of(timezoneApplicableForTestcase)
    val instantOfStartTZCal = instantOfStart.atZone(zoneToUseCal)
    val instantOfStartTZUTC = instantOfStartTZCal.withZoneSameInstant(ZoneOffset.UTC)


    val evInitiated = EvInitiated(gameStartsAt)
    val evQuizIsFinalized = HuddleGame.EvQuizIsFinalized(gameStartsAt + 1, "Some metadata")
    val evQuestionAnswered_1 = HuddleGame.EvQuestionAnswered(gameStartsAt + 2, questionaAndAnswers(0))
    val evQuestionAnswered_2 = HuddleGame.EvQuestionAnswered(gameStartsAt + 4, questionaAndAnswers(1))
    val evSessionEndedByPlayer = HuddleGame.EvEndedByPlayer(gameStartsAt + 6, 10)

    val liveboardInformerProbe = TestProbe()

    val liveboardInfomer = system.actorOf(DummyLiveBoardServiceGatewayActor(dummyLiveboardServiceEndpoint),"DummyLiveBoardServiceGatewayActor-UUID-4")

    val custodianActorName = s"GameSessionCustodianActor-${gameSessionInfo.gameSessionUUID}"

    val custodian = system.actorOf(
      GameSessionCustodianActor(
        gameSessionInfo,
        redisHost,
        redisPort,
        maxGameSessionLifetime,
        liveboardInformerProbe.ref,
        adminPanelNotifierActor,
        lrsProbe.ref,
        ""
      ), custodianActorName)

    custodian ! evInitiated

    expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, "Initiated"), Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID))))

    custodian ! evQuizIsFinalized

    expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "Prepared")))

    custodian ! evQuestionAnswered_1

    expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))

    custodian ! evQuestionAnswered_2

    expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "QuestionAnswered")))

    custodian ! evSessionEndedByPlayer

    expectMsg(HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, "Ended")))

    liveboardInformerProbe.expectMsg(
      Duration(5, "seconds"),
      LeaderBoardConsumableData(
        gameSessionInfo.companyID,
        gameSessionInfo.departmentID,
        gameSessionInfo.gameID,
        gameSessionInfo.playerID,
        gameSessionInfo.groupID,
        gameSessionInfo.gameSessionUUID,
        (evQuestionAnswered_1.questionAndAnswer.points + evQuestionAnswered_2.questionAndAnswer.points)
      ))

    liveboardInformerProbe.forward(liveboardInfomer)

    /*val k = fetchGameSessionFromDB(gameSessionInfo) // 'k' is a list

    assert(
      !k.isEmpty                             &&
        k.head != NonExistentComputedGameSession &&
        k.tail.isEmpty                    &&  // Because we expect only one record with corresponding keyfields to exist
        k.head.companyID             == gameSessionInfo.companyID            &&
        k.head.departmentID          == gameSessionInfo.departmentID         &&
        k.head.gameID                == gameSessionInfo.gameID               &&
        k.head.playerID              == gameSessionInfo.playerID             &&
        k.head.gameType              == gameSessionInfo.gameType             &&
        k.head.gameSessionUUID       == gameSessionInfo.gameSessionUUID      &&
        k.head.endedBecauseOf        == GameSessionEndedByPlayer.toString   &&
        k.head.totalPointsObtained   == (evQuestionAnswered_1.questionAndAnswer.points +
                                         evQuestionAnswered_2.questionAndAnswer.points)
    )

  }

  def retrieve(
                c: Connection, companyID: String, department: String, gameID: String, playerID: String, gameSessionUUID: String)
  : List[ComputedGameSession] = {

    val e = DSL.using(c, SQLDialect.MARIADB)
    val x = GAMESESSIONRECORDS as "x"

    val k = e.selectFrom(GAMESESSIONRECORDS)
      .where(GAMESESSIONRECORDS.COMPANYID.eq(companyID))
      .and(GAMESESSIONRECORDS.BELONGSTODEPARTMENT.eq(department))
      .and(GAMESESSIONRECORDS.GAMEID.eq(gameID))
      .and(GAMESESSIONRECORDS.PLAYERID.eq(playerID))
      .and(GAMESESSIONRECORDS.GAMESESSIONUUID.eq(gameSessionUUID))
      .fetchInto(classOf[DBActionGameSessionRecord])
      .asScala
      .toList

    // TODO:
    // Consider retrieving a record of PlayerPerformance and treat that as a Record type.
    // What if we return a list of Records from this method, keeping the equivalence between
    // Select and Upsert (below)? Does that bring any extra cleaner interface?

    transformDBRecordToComputedGameSession(
      if (k.isEmpty)
        List(NonExistentGameSessionRecord)
      else k
    )
  }

    private def
  fetchGameSessionFromDB(gameSessionInfo: GameSession): List[ComputedGameSession] = {

    GameSessionDBButlerActor.retrieve(
      DriverManager.getConnection(dbAccessURL),
      gameSessionInfo.companyID,
      gameSessionInfo.departmentID,
      gameSessionInfo.gameID,
      gameSessionInfo.playerID,
      gameSessionInfo.gameSessionUUID
    )

  }*/
  }
}

package example.org.nirmalya.experiments


import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, FSM, Props}
import com.redis.RedisClient
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.HuddleGame._
import GameSessionHandlingServiceProtocol.{HuddleGame, NonExistingCompleteGamePlaySessionHistory, RecordingStatus, _}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.duration.{Duration, TimeUnit}


/**
  * Created by nirmalya on 5/6/17.
  */
class GamePlayRecorderActor(val cleanDataOnExit: Boolean, val seededWithSession: GameSession) extends FSM [HuddleGameState, HuddleGameFSMData] with ActorLogging {

  //TODO
  // 1) Pick Redis configurationn values from Akka Config, instead of hardcoding
  // 2) Pick Timeout value from Akka config, instead of hardcoding

  val redisClient = new RedisClient("127.0.0.1", 6379)

  val maxGameTimeout = Duration(5, TimeUnit.SECONDS)
  //config.useSingleServer().setAddress("127.0.0.1:6379")

   startWith(GameYetToStartState, DataToBeginWith)

   when (HuddleGame.GameYetToStartState, maxGameTimeout) {

     case Event(gameStarted: HuddleGame.EvStarted, _) =>

          sender ! recordStartOfTheGame(gameStarted.startedAt, gameStarted.gameSession)
          goto (HuddleGame.GameHasStartedState) using DataToCleanUpRedis(gameStarted.gameSession)

   }

   when (HuddleGame.GameHasStartedState, maxGameTimeout) {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

       sender ! recordThatAQuestionIsAnswered(
                      questionAnswered.receivedAt,
                      questionAnswered.questionAndAnswer,
                      questionAnswered.gameSession
                )
       goto (HuddleGame.GameIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
       goto (HuddleGame.GameIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       recordEndOfTheGame(System.currentTimeMillis, GameEndedByTimeOut, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameIsWrappingUpState)

   }

   when (HuddleGame.GameIsContinuingState, maxGameTimeout)  {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
       sender ! recordThatAQuestionIsAnswered(
         questionAnswered.receivedAt,
         questionAnswered.questionAndAnswer,
         questionAnswered.gameSession
       )
       stay

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
       self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
       goto (HuddleGame.GameIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       recordEndOfTheGame(System.currentTimeMillis, GameEndedByTimeOut, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameIsWrappingUpState)

   }

  when (HuddleGame.GameIsPausedState, maxGameTimeout)  {

    case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
      sender ! recordThatAQuestionIsAnswered(
        questionAnswered.receivedAt,
        questionAnswered.questionAndAnswer,
        questionAnswered.gameSession
      )
      goto (HuddleGame.GameIsContinuingState)

    case Event(ended: HuddleGame.EvEnded, _)                     =>

      sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
      self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
      goto (HuddleGame.GameIsWrappingUpState)

    case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

      log.info("In PuasedState, time out occurred.. writing record to REDIS")
      recordEndOfTheGame(System.currentTimeMillis, GameEndedByTimeOut, sessionReqdForCleaningUp.gameSession)
      self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
      goto (HuddleGame.GameIsWrappingUpState)

  }

  when (HuddleGame.GameIsWrappingUpState) {

    case Event(cleanUp: HuddleGame.EvCleanUpRequired, _) =>

      //TODO: Call REST endpoint to store the GameSession
      //TODO: Replace the if-check below, with a HOF
       if (!this.cleanDataOnExit) removeGameSessionFromREDIS(cleanUp.gameSession)
       stop(FSM.Normal, DataToCleanUpRedis(cleanUp.gameSession))
  }

  whenUnhandled {

    case Event(m, d) =>

      m match  {

        case EvGamePlayRecordSoFarRequired(gameSession) =>
             sender ! extractCurrentGamePlayRecord(gameSession)

        case _  =>
          log.info(s"Unknown message of type ${m.getClass}, in ${FSM.CurrentState}")
      }

      stay
  }

  onTransition {

    case HuddleGame.GameYetToStartState -> HuddleGame.GameHasStartedState =>
      log.info("Transition from GameYetToStart GameHasStarted")
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.info(s"Game ${data.asInstanceOf[HuddleGame.DataToCleanUpRedis].gameSession}, is finished.")
  }

  override def postStop(): Unit = {
    super.postStop()
    this.redisClient.disconnect
  }

  private
  def gameSessionAlreadyExists (gameSession: GameSession): Boolean = this.redisClient.exists(gameSession)


  private
  def recordStartOfTheGame(atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {

    // TODO: Handle this during transition from YetToStart to Started
    // val createdTuple = write[GameCreatedTuple](GameCreatedTuple("Game Sentinel"))
    val completeSessionSoFar = write[CompleteGamePlaySessionHistory](
      CompleteGamePlaySessionHistory(
        List(GameCreatedTupleInREDIS("Game Sentinel")))
      )
    this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)

    RecordingStatus (
          storeSessionHistory(gameSession,GameStartedTupleInREDIS(atServerClockTime)) match {

            case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
            case OKRedisSessionStatus        => s"Game session ($gameSession), start recorded"
          })

  }

  private
  def recordThatAQuestionIsAnswered(
        atServerClockTime: Long, questionNAnswer: QuestionAnswerTuple, gameSession: GameSession): RecordingStatus = {

    // val playTuple = GamePlayTuple(atServerClockTime,questionNAnswer)

    RecordingStatus (
      storeSessionHistory(gameSession,GamePlayTupleInREDIS(atServerClockTime,questionNAnswer)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}, question(${questionNAnswer.questionID},${questionNAnswer.answerID}"
        case OKRedisSessionStatus        => s"Game session ($gameSession), question(${questionNAnswer.questionID},${questionNAnswer.answerID} recorded"
      }
    )
  }

  private
  def recordAPauseOfTheGame(
        atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {
    RecordingStatus (
      storeSessionHistory(gameSession,GamePausedTupleInREDIS(atServerClockTime)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
        case OKRedisSessionStatus        => s"Game session ($gameSession), Pause($atServerClockTime) recorded"
      }
    )

  }

  private
  def recordEndOfTheGame(
            atServerClockTime: Long, endedHow: GameEndingReason, gameSession: GameSession): RecordingStatus = {

    RecordingStatus (
      storeSessionHistory(gameSession,GameEndedTupleInREDIS(atServerClockTime, endedHow.toString)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
        case OKRedisSessionStatus        => s"Game session ($gameSession), End($atServerClockTime) recorded"
      }
    )
  }

  private
  def removeGameSessionFromREDIS (gameSession: GameSession): RecordingStatus = {

    RecordingStatus(
      this.redisClient.del(gameSession) match {

        case Some(n) => s"Game ($gameSession), ($n) key deleted."
        case None    => s"Game ($gameSession), no such key found"
      }
    )
  }

  private
  def extractCurrentGamePlayRecord (gameSession: GameSession): RecordingStatus = {

    val gamePlayRecord = this.redisClient.hget(gameSession, "SessionHistory")

    RecordingStatus(
      gamePlayRecord match {

          case Some(s)  => s
          case None     => s"NotFound: GamePlayRecord for ($gameSession)"

      }
    )
  }

  private def
  storeSessionHistory(gameSession: GameSession, latestRecord: GameInfoTupleInREDIS): RedisSessionStatus = {

    val historySoFar = retrieveSessionHistory(gameSession, "SessionHistory")

    if (historySoFar == NonExistingCompleteGamePlaySessionHistory) {
      log.info(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
      FailedRedisSessionStatus(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
    }
    else {
      val updatedHistory = historySoFar.elems :+ latestRecord
      val completeSessionSoFar = write[CompleteGamePlaySessionHistory](CompleteGamePlaySessionHistory(updatedHistory))
      this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)
      log.info(s"Game Session:Key ($gameSession:SessionHistory) updated with ($latestRecord)")
      OKRedisSessionStatus
    }
  }

  private def
  retrieveSessionHistory (gameSession: GameSession, key: String): CompleteGamePlaySessionHistory = {

    val historySoFar = this.redisClient.hget(gameSession, key)

    // println(historySoFar)

    historySoFar match {
      case Some(record) => parse(record).extract[CompleteGamePlaySessionHistory]
      case None         => NonExistingCompleteGamePlaySessionHistory

    }
  }
}

object GamePlayRecorderActor {
  def apply(shouldCleanUpREDIS: Boolean, sessionID: GameSession): Props = Props(new GamePlayRecorderActor(shouldCleanUpREDIS, sessionID))
}

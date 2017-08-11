package example.org.nirmalya.experiments

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.redis.RedisClient
import GameSessionHandlingServiceProtocol.{HuddleGame, NonExistingCompleteGamePlaySessionHistory, RecordingStatus, _}
import GameSessionHandlingServiceProtocol.HuddleGame._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.duration.{Duration, FiniteDuration, TimeUnit}

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by nirmalya on 5/6/17.
  */
class GamePlayRecorderActor(val cleanDataOnExit: Boolean,
                            val seededWithSession: GameSession,
                            val redisHost: String,
                            val redisPort: Int,
                            val maxGameTimeOut: FiniteDuration,
                            val sessionCompletionEvReceiverActor: ActorRef
                           ) extends FSM [HuddleGameSessionState, HuddleGameFSMData] with ActorLogging {

  //TODO
  // 1) Pick Timeout value from Akka config, instead of hardcoding
  // 2) We need to manage the connection pool of REDIS. Important.


  val redisClient = new RedisClient(redisHost, redisPort)

  // It is possible that even after a GameSession is created, the Player never plays. We don't want the GameSession to hang around,
  // needlessly. So, to deal with such a case, we schedule a reminder, so that after a expiration of a maximum timeout duration,
  // the Actor is destroyed.
  val gameNeverStartedIndicator = context.system.scheduler.scheduleOnce(this.maxGameTimeOut, self, HuddleGame.EvGameShouldHaveStartedByNow)

   startWith(GameSessionYetToStartState, DataToBeginWith)

   when (HuddleGame.GameSessionYetToStartState, this.maxGameTimeOut) {

     case Event(gameStarted: HuddleGame.EvInitiated, _) =>

          this.gameNeverStartedIndicator.cancel
          sender ! recordStartOfTheGame(gameStarted.startedAt, gameStarted.gameSession)
          goto (HuddleGame.GameSessionIsBeingPreparedState) using DataToCleanUpRedis(gameStarted.gameSession)

     case Event(HuddleGame.EvGameShouldHaveStartedByNow, _ ) =>

       recordEndOfTheGame(System.currentTimeMillis, GameSessionCreatedButNotStarted, -1, seededWithSession)
       self ! HuddleGame.EvCleanUpRequired (seededWithSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsBeingPreparedState, this.maxGameTimeOut) {

     case Event(setOfQuestions: EvQuizIsFinalized, _)   =>

       sender ! recordQuizSet(setOfQuestions.finalizedAt, setOfQuestions.questionMetadata, setOfQuestions.gameSession)
       goto (HuddleGame.GameSessionHasStartedState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

       recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)
   }

   when (HuddleGame.GameSessionHasStartedState, this.maxGameTimeOut) {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

       sender ! recordThatAQuestionIsAnswered(
                      questionAnswered.receivedAt,
                      questionAnswered.questionAndAnswer,
                      questionAnswered.gameSession
                )
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsContinuingState, this.maxGameTimeOut)  {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
       sender ! recordThatAQuestionIsAnswered(
         questionAnswered.receivedAt,
         questionAnswered.questionAndAnswer,
         questionAnswered.gameSession
       )
       stay

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
       self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsPausedState, this.maxGameTimeOut)  {

      case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
        sender ! recordThatAQuestionIsAnswered(
          questionAnswered.receivedAt,
          questionAnswered.questionAndAnswer,
          questionAnswered.gameSession
        )
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

        sender ! recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(ended: HuddleGame.EvEnded, _)                     =>

        sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
        self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
        goto (HuddleGame.GameSessionIsWrappingUpState)

      case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

        recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
        self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
        goto (HuddleGame.GameSessionIsWrappingUpState)

    }

   when (HuddleGame.GameSessionIsWrappingUpState) {

      case Event(cleanUp: HuddleGame.EvCleanUpRequired, _) =>

        //Call REST endpoint to store the GameSession

        val currentRecord = this.extractCurrentGamePlayRecord(cleanUp.gameSession)
        this.sessionCompletionEvReceiverActor ! EmittedWhenGameSessionIsFinished(currentRecord.details)

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
          log.info(s"Unknown message of type ${m.getClass}, in ${this.stateName}")
      }

      stay
  }

  onTransition {

    case HuddleGame.GameSessionYetToStartState -> HuddleGame.GameSessionHasStartedState =>
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

  private def
  recordQuizSet (atServerClockTime: Long, questionMetadata: String, gameSession: GameSession): RecordingStatus = {

    RecordingStatus (
      storeSessionHistory(gameSession,GamePreparedTupleInREDIS(atServerClockTime,questionMetadata)) match {

        case f: FailedRedisSessionStatus =>
          s"Failure: ${f.reason}, sessionID($gameSession), Metadata:(${questionMetadata}"
        case OKRedisSessionStatus        =>
          s"sessionID($gameSession), Quiz set up (${questionMetadata})."
      }
    )
  }


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
          storeSessionHistory(gameSession,GameInitiatedTupleInREDIS(atServerClockTime)) match {

            case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
            case OKRedisSessionStatus        => s"sessionID($gameSession), Created."
          })

  }

  private
  def recordThatClipIsPlayed(atServerClockTime: Long, clipName: String, gameSession: GameSession): RecordingStatus = {

    RecordingStatus (
      storeSessionHistory(gameSession,GameClipRunInREDIS(atServerClockTime,clipName)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}, sessionID($gameSession), clip(${clipName}"
        case OKRedisSessionStatus        => s"sessionID($gameSession), ClipPlayed(name:${{clipName}})."
      }
    )
  }

  private
  def recordThatAQuestionIsAnswered(
        atServerClockTime: Long, questionNAnswer: QuestionAnswerTuple, gameSession: GameSession): RecordingStatus = {

    // val playTuple = GamePlayTuple(atServerClockTime,questionNAnswer)

    RecordingStatus (
      storeSessionHistory(gameSession,GamePlayedTupleInREDIS(atServerClockTime,questionNAnswer)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}, sessionID($gameSession), question(${questionNAnswer.questionID},${questionNAnswer.answerID}"
        case OKRedisSessionStatus        => s"sessionID($gameSession), Played(Q:${questionNAnswer.questionID},A:${questionNAnswer.answerID})."
      }
    )
  }

  private
  def recordAPauseOfTheGame(
        atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {
    RecordingStatus (
      storeSessionHistory(gameSession,GamePausedTupleInREDIS(atServerClockTime)) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
        case OKRedisSessionStatus        => s"sessionID($gameSession), Paused."
      }
    )

  }

  private
  def recordEndOfTheGame(
            atServerClockTime: Long, endedHow: GameSessionEndingReason, totalTimeTakenByPlayer: Int, gameSession: GameSession
  ): RecordingStatus = {


    RecordingStatus (
      storeSessionHistory(gameSession,GameEndedTupleInREDIS(atServerClockTime, endedHow.toString,totalTimeTakenByPlayer )) match {

        case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
        case OKRedisSessionStatus        => s"sessionID($gameSession), Ended."
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
  def apply(shouldCleanUpREDIS: Boolean,
            sessionID: GameSession,
            redisHost: String,
            redisPort: Int,
            maxGameTimeOut: FiniteDuration,
            sessionCompletionEvReceiver: ActorRef
           ): Props =
    Props(new GamePlayRecorderActor(shouldCleanUpREDIS, sessionID, redisHost, redisPort, maxGameTimeOut,sessionCompletionEvReceiver))
}

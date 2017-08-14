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
                            val maxGameSessionLifetime: FiniteDuration,
                            val sessionCompletionEvReceiverActor: ActorRef
                           ) extends FSM [HuddleGameSessionState, HuddleGameFSMData] with ActorLogging {

  //TODO
  // 1) Pick Timeout value from Akka config, instead of hardcoding
  // 2) We need to manage the connection pool of REDIS. Important.


  val redisClient = new RedisClient(redisHost, redisPort)

  // It is possible that even after a GameSession is created, the Player never plays. We don't want the GameSession to hang around,
  // needlessly. So, to deal with such a case, we schedule a reminder, so that after a expiration of a maximum timeout duration,
  // the Actor is destroyed.
  val gameNeverStartedIndicator = context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveStartedByNow)

   startWith(GameSessionYetToStartState, DataToBeginWith)

   when (HuddleGame.GameSessionYetToStartState, this.maxGameSessionLifetime) {

     case Event(gameInitiated: HuddleGame.EvInitiated, _) =>

          this.gameNeverStartedIndicator.cancel
          sender ! recordInitiationOfTheGame(gameInitiated.startedAt, gameInitiated.gameSession)
          goto (HuddleGame.GameSessionIsBeingPreparedState) using DataToCleanUpRedis(gameInitiated.gameSession)

     case Event(HuddleGame.EvGameShouldHaveStartedByNow, _ ) =>

       recordEndOfTheGame(System.currentTimeMillis, GameSessionCreatedButNotStarted, -1, seededWithSession)
       self ! HuddleGame.EvCleanUpRequired (seededWithSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsBeingPreparedState, this.maxGameSessionLifetime) {

     case Event(setOfQuestions: EvQuizIsFinalized, _)   =>

       sender ! recordPreparationOfTheGame(setOfQuestions.finalizedAt, setOfQuestions.questionMetadata, setOfQuestions.gameSession)
       goto (HuddleGame.GameSessionHasStartedState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

       recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)
   }

   when (HuddleGame.GameSessionHasStartedState, this.maxGameSessionLifetime) {

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

   when (HuddleGame.GameSessionIsContinuingState, this.maxGameSessionLifetime)  {

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

   when (HuddleGame.GameSessionIsPausedState, this.maxGameSessionLifetime)  {

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
          stay

        case EvForceEndedByManager(t,e,n,g) =>

          val hasEndedSuccessfully = recordEndOfTheGame(t, GameSessionEndedByManager, -1, g)

          if (hasEndedSuccessfully.details == "Ended") {
            log.info(s"GameSession ($g), forced to end by manager ($n), at ($t)")
            sender ! hasEndedSuccessfully
            self ! EvCleanUpRequired(g)
            goto (GameSessionIsWrappingUpState)
          }
          else {
            log.info(s"GameSession ($g), failed to end by manager ($n), at ($t)")
            sender ! hasEndedSuccessfully
            stay
          }


        case _  =>
          log.info(s"Unknown message of type ${m.getClass}, in ${this.stateName}")
          stay
      }


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
  recordPreparationOfTheGame (atServerClockTime: Long, questionMetadata: String, gameSession: GameSession): RecordingStatus = {

    RecordingStatus (
      storeSessionHistory(gameSession,GamePreparedTupleInREDIS(atServerClockTime,questionMetadata)) match {

        case f: FailedRedisSessionStatus =>
          log.debug(s"Failure: (${f.reason}), Op: recordPreparation, sessionID($gameSession), Metadata:(${questionMetadata})")
          ("Not Prepared")
        case OKRedisSessionStatus        =>
          log.debug(s"Success, Op: recordPreparation, sessionID($gameSession), Quiz set up (${questionMetadata})")
          ("Prepared")
      }
    )
  }


  private
  def recordInitiationOfTheGame(atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {

    // TODO: Handle this during transition from YetToStart to Started
    // val createdTuple = write[GameCreatedTuple](GameCreatedTuple("Game Sentinel"))
    val completeSessionSoFar = write[CompleteGamePlaySessionHistory](
      CompleteGamePlaySessionHistory(
        List(GameCreatedTupleInREDIS("Game Sentinel")))
      )
    this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)

    RecordingStatus (
          storeSessionHistory(gameSession,GameInitiatedTupleInREDIS(atServerClockTime)) match {

            case f: FailedRedisSessionStatus =>
              log.debug(s"Failure: (${f.reason}), Op: recordInitiation, sessionID($gameSession)")
              ("Not Initiated")
            case OKRedisSessionStatus        =>
              log.debug(s"Success, Op: recordInitiation, sessionID($gameSession)")
              ("Initiated")
          })

  }

  private
  def recordThatClipIsPlayed(atServerClockTime: Long, clipName: String, gameSession: GameSession): RecordingStatus = {

    RecordingStatus (
      storeSessionHistory(gameSession,GameClipRunInREDIS(atServerClockTime,clipName)) match {

        case f: FailedRedisSessionStatus =>
          log.debug(s"Failure: ${f.reason}, op: recordClipPlayed, sessionID($gameSession), clip(${clipName}")
          ("Not Clip Played")
        case OKRedisSessionStatus        =>
          log.debug(s"Success, Op: recordClipPlayed, sessionID($gameSession)")
          ("Clip Played")
      }
    )
  }

  private
  def recordThatAQuestionIsAnswered(
        atServerClockTime: Long, questionNAnswer: QuestionAnswerTuple, gameSession: GameSession): RecordingStatus = {

    // val playTuple = GamePlayTuple(atServerClockTime,questionNAnswer)

    RecordingStatus (
      storeSessionHistory(gameSession,GamePlayedTupleInREDIS(atServerClockTime,questionNAnswer)) match {

        case f: FailedRedisSessionStatus =>
          log.debug(s"Failure: ${f.reason}, op: QuestionAnswered, sessionID($gameSession), question(${questionNAnswer.questionID},${questionNAnswer.answerID}")
          ("Not QuestionAnswered")
        case OKRedisSessionStatus        =>
          log.debug(s"Sucess: op: QuestionAnswered, sessionID($gameSession), Played(Q:${questionNAnswer.questionID},A:${questionNAnswer.answerID})")
          ("QuestionAnswered")
      }
    )
  }

  private
  def recordAPauseOfTheGame(
        atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {
    RecordingStatus (
      storeSessionHistory(gameSession,GamePausedTupleInREDIS(atServerClockTime)) match {

        case f: FailedRedisSessionStatus =>
          log.debug(s"Failure: ${f.reason}, op: Paused, sessionID($gameSession)")
          ("Not Paused")
        case OKRedisSessionStatus        =>
          log.debug(s"Success: op: Paused, sessionID($gameSession)")
          ("Paused")
      }
    )

  }

  private
  def recordEndOfTheGame(
            atServerClockTime: Long, endedHow: GameSessionEndingReason, totalTimeTakenByPlayer: Int, gameSession: GameSession
  ): RecordingStatus = {


    RecordingStatus (
      storeSessionHistory(gameSession,GameEndedTupleInREDIS(atServerClockTime, endedHow.toString,totalTimeTakenByPlayer )) match {

        case f: FailedRedisSessionStatus =>
          log.debug(s"Failure: ${f.reason}, op: Ended, sessionID($gameSession), how(${endedHow.toString})")
          "Not Ended"
        case OKRedisSessionStatus        =>
          log.debug(s"Success, op: Ended, sessionID($gameSession), how(${endedHow.toString})")
          "Ended"
      }
    )
  }

  private
  def removeGameSessionFromREDIS (gameSession: GameSession): RecordingStatus = {

    RecordingStatus(
      this.redisClient.del(gameSession) match {

        case Some(n) =>
          log.debug(s"Success, op: Removal, Game ($gameSession), ($n) key removed")
          "Removed"
        case None    =>
          log.debug(s"Failure, op: Removal, Game ($gameSession), no such key found")
          "Not Removed"
      }
    )
  }

  private
  def extractCurrentGamePlayRecord (gameSession: GameSession): RecordingStatus = {

    val gamePlayRecord = this.redisClient.hget(gameSession, "SessionHistory")

    RecordingStatus(
      gamePlayRecord match {

          case Some(retrievedRec)  =>
            log.debug(s"Success, op: Extraction, Game ($gameSession)")
            retrievedRec
          case None     =>
            log.debug(s"Failure, op: Extraction, GamePlayRecord for ($gameSession) not found")
            ("Not Found")
      }
    )
  }

  private def
  storeSessionHistory(gameSession: GameSession, latestRecord: GameInfoTupleInREDIS): RedisSessionStatus = {

    val historySoFar = retrieveSessionHistory(gameSession, "SessionHistory")

    if (historySoFar == NonExistingCompleteGamePlaySessionHistory) {
      log.info(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
      FailedRedisSessionStatus(s"($gameSession:SessionHistory) not found.")
    }
    else {
      val updatedHistory = historySoFar.elems :+ latestRecord
      val completeSessionSoFar = write[CompleteGamePlaySessionHistory](CompleteGamePlaySessionHistory(updatedHistory))
      this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)
      log.debug(s"($gameSession:SessionHistory) updated with ($latestRecord)")
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

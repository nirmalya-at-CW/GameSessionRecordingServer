package example.org.nirmalya.experiments

import akka.actor.{ActorLogging, ActorRef, Cancellable, FSM, Props}
import com.redis.RedisClient
import GameSessionHandlingServiceProtocol.{HuddleGame, NonExistingCompleteGamePlaySessionHistory, RecordingStatus, _}
import GameSessionHandlingServiceProtocol.HuddleGame._
import example.org.nirmalya.experiments.MariaDBAware.GameSessionRecordButlerActor
import example.org.nirmalya.experiments.RedisAware.RedisButlerGameSessionRecording
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.duration.{Duration, FiniteDuration, TimeUnit}
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by nirmalya on 5/6/17.
  */
class GameSessionStateHolderActor(val cleanDataOnExit: Boolean,
                                  val seededWithSession: GameSession,
                                  val redisHost: String,
                                  val redisPort: Int,
                                  val maxGameSessionLifetime: FiniteDuration,
                                  val sessionCompletionEvReceiverActor: ActorRef
                           ) extends FSM [HuddleGameSessionState, HuddleGameFSMData] with ActorLogging {

  // TODO: pick these parameters from config
  val dbConnectionString = "jdbc:mariadb://localhost:3306/OneHuddle?user=nuovo&password=nuovo123"

  //TODO: instead of making this a direct child, we should create a router and encapsulate that with HTTP apis.
  val gameSessionRecordDBButler =
          context.system.actorOf(GameSessionRecordButlerActor(dbConnectionString, context.dispatcher))

  val redisButler = new RedisButlerGameSessionRecording(redisHost, redisPort)

  // It is possible that even after a GameSession is created, the Player never plays. We don't want the GameSession to hang around,
  // needlessly. So, to deal with such a case, we schedule a reminder, so that after a expiration of a maximum timeout duration,
  // the Actor is destroyed.
  val gameNeverStartedIndicator = context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveStartedByNow)

   //var gameSessionMaxTimeIsUpIndicator: Cancellable =  //context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveStartedByNow)

   startWith(GameSessionYetToStartState, DataToBeginWith)

   when (HuddleGame.GameSessionYetToStartState, this.maxGameSessionLifetime) {

     case Event(gameInitiated: HuddleGame.EvInitiated, _) =>

          this.gameNeverStartedIndicator.cancel
          sender ! redisButler.recordInitiationOfTheGame(gameInitiated.startedAt, gameInitiated.gameSession)

          // We need to set up a separate timer, to indicate when the maximum time for this session is up
          context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveEndedByNow)
          goto (HuddleGame.GameSessionIsBeingPreparedState) using DataToCleanUpRedis(gameInitiated.gameSession)

     case Event(HuddleGame.EvGameShouldHaveStartedByNow, _ ) =>

       redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionCreatedButNotStarted, -1, seededWithSession)
       self ! HuddleGame.EvCleanUpRequired (seededWithSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsBeingPreparedState, this.maxGameSessionLifetime) {

     case Event(setOfQuestions: EvQuizIsFinalized, _)   =>

       sender ! redisButler.recordPreparationOfTheGame(setOfQuestions.finalizedAt, setOfQuestions.questionMetadata, setOfQuestions.gameSession)
       goto (HuddleGame.GameSessionHasStartedState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

       redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)
   }

   when (HuddleGame.GameSessionHasStartedState, this.maxGameSessionLifetime) {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

       sender ! redisButler.recordThatAQuestionIsAnswered(
                      questionAnswered.receivedAt,
                      questionAnswered.questionAndAnswer,
                      questionAnswered.gameSession
                )
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! redisButler.recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! redisButler.recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! redisButler.recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsContinuingState, this.maxGameSessionLifetime)  {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
       sender ! redisButler.recordThatAQuestionIsAnswered(
         questionAnswered.receivedAt,
         questionAnswered.questionAndAnswer,
         questionAnswered.gameSession
       )
       stay

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! redisButler.recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! redisButler.recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEnded, _)                     =>

       sender ! redisButler.recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
       self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

     case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>
       redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
       self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsPausedState, this.maxGameSessionLifetime)  {

      case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>
        sender ! redisButler.recordThatAQuestionIsAnswered(
          questionAnswered.receivedAt,
          questionAnswered.questionAndAnswer,
          questionAnswered.gameSession
        )
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

        sender ! redisButler.recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, aboutToPlayClip.gameSession)
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(ended: HuddleGame.EvEnded, _)                     =>

        sender ! redisButler.recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.totalTimeTakenByPlayer, ended.gameSession)
        self ! HuddleGame.EvCleanUpRequired (ended.gameSession)
        goto (HuddleGame.GameSessionIsWrappingUpState)

      case Event(StateTimeout, sessionReqdForCleaningUp:DataToCleanUpRedis)  =>

        redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, sessionReqdForCleaningUp.gameSession)
        self ! HuddleGame.EvCleanUpRequired (sessionReqdForCleaningUp.gameSession)
        goto (HuddleGame.GameSessionIsWrappingUpState)

    }

   when (HuddleGame.GameSessionIsWrappingUpState) {

      case Event(cleanUp: HuddleGame.EvCleanUpRequired, _) =>

        val p = prepareGameSessionForDB()

        val currentRecord = redisButler.extractCurrentGamePlayRecord(cleanUp.gameSession)
        this.sessionCompletionEvReceiverActor ! EmittedWhenGameSessionIsFinished(currentRecord.details)

        //TODO: Replace the if-check below, with a HOF
         if (!this.cleanDataOnExit) redisButler.removeGameSessionFromREDIS(cleanUp.gameSession)
         stop(FSM.Normal, DataToCleanUpRedis(cleanUp.gameSession))
    }

  whenUnhandled {

    case Event(m, d) =>

      m match  {

        case EvGamePlayRecordSoFarRequired(gameSession) =>
             sender ! redisButler.extractCurrentGamePlayRecord(gameSession)
          stay

        case EvForceEndedByManager(t,e,n,g) =>

          val hasEndedSuccessfully = redisButler.recordEndOfTheGame(t, GameSessionEndedByManager, -1, g)

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

        case EvGameShouldHaveEndedByNow =>

          redisButler.recordEndOfTheGame(System.currentTimeMillis, GameSessionEndedByTimeOut, -1, seededWithSession)
          self ! HuddleGame.EvCleanUpRequired (seededWithSession)
          goto (HuddleGame.GameSessionIsWrappingUpState)

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
    this.redisButler.releaseConnection
  }
}

object GameSessionStateHolderActor {
  def apply(shouldCleanUpREDIS: Boolean,
            sessionID: GameSession,
            redisHost: String,
            redisPort: Int,
            maxGameTimeOut: FiniteDuration,
            sessionCompletionEvReceiver: ActorRef
           ): Props =
    Props(new GameSessionStateHolderActor(shouldCleanUpREDIS, sessionID, redisHost, redisPort, maxGameTimeOut,sessionCompletionEvReceiver))
}

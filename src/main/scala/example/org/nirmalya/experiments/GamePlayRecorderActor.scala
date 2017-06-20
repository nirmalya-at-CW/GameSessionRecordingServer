package example.org.nirmalya.experiments


import akka.actor.{AbstractActor, Actor, ActorLogging, FSM, Props}
import com.redis.RedisClient
import example.org.nirmalya.experiments.protocol.HuddleGame._
import protocol.{HuddleGame, NonExistingCompleteGamePlaySessionHistory, RecordingStatus, _}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s.native.Json


/**
  * Created by nirmalya on 5/6/17.
  */
class GamePlayRecorderActor extends FSM [HuddleGameState, HuddleGameFSMData] with ActorLogging {

  //TODO
  // 1) Pick Redis configurationn values from Akka Config, instead of hardcoding
  // 2)

  val redisClient = new RedisClient("127.0.0.1", 6379)
  //config.useSingleServer().setAddress("127.0.0.1:6379")

   startWith(GameYetToStart, DataToBeginWith)

   when (HuddleGame.GameYetToStart) {

     case Event(gameStarted: HuddleGame.Started, _) =>

          sender ! recordStartOfTheGame(gameStarted.startedAt, gameStarted.gameSession)
          goto (HuddleGame.GameHasStarted)

   }

   when (HuddleGame.GameHasStarted) {

     case Event(questionAnswered: HuddleGame.QuestionAnswered, _) =>

       sender ! recordThatAQuestionIsAnswered(
                      questionAnswered.receivedAt,
                      questionAnswered.questionAndAnswer,
                      questionAnswered.gameSession
                )
       goto (HuddleGame.GameIsContinuing)

     case Event(paused: HuddleGame.Paused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameIsPaused)

     case Event(ended: HuddleGame.Ended, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
       goto (HuddleGame.GameIsWrappingUp)

   }

   when (HuddleGame.GameIsContinuing)  {

     case Event(questionAnswered: HuddleGame.QuestionAnswered, _) =>
       sender ! recordThatAQuestionIsAnswered(
         questionAnswered.receivedAt,
         questionAnswered.questionAndAnswer,
         questionAnswered.gameSession
       )
       stay

     case Event(paused: HuddleGame.Paused, _)                     =>

       sender ! recordAPauseOfTheGame(paused.pausedAt, paused.gameSession)
       goto (HuddleGame.GameIsPaused)

     case Event(ended: HuddleGame.Ended, _)                     =>

       sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
       self ! HuddleGame.CleanUpRequired (ended.gameSession)
       goto (HuddleGame.GameIsWrappingUp)

   }

  when (HuddleGame.GameIsPaused)  {

    case Event(questionAnswered: HuddleGame.QuestionAnswered, _) =>
      sender ! recordThatAQuestionIsAnswered(
        questionAnswered.receivedAt,
        questionAnswered.questionAndAnswer,
        questionAnswered.gameSession
      )
      goto (HuddleGame.GameIsContinuing)

    case Event(ended: HuddleGame.Ended, _)                     =>

      sender ! recordEndOfTheGame(ended.endedAt, ended.endedBy, ended.gameSession)
      self ! HuddleGame.CleanUpRequired (ended.gameSession)
      goto (HuddleGame.GameIsWrappingUp)
  }

  when (HuddleGame.GameIsWrappingUp) {

    case Event(cleanUp: HuddleGame.CleanUpRequired, _) =>

      //TODO: Call REST endpoint to store the GameSession
       removeGameSession(cleanUp.gameSession)
       stop(FSM.Normal, DataToCleanUpRedis(cleanUp.gameSession))
  }

  whenUnhandled {

    case Event(m, d) =>

      m match  {

        case GamePlayRecordSoFarRequired(gameSession) =>
             sender ! extractCurrentGamePlayRecord(gameSession)

        case _  =>
          log.info(s"Unknown message of type ${m.getClass}, in ${FSM.CurrentState}")
      }

      stay
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.info(s"Game ${data.asInstanceOf[HuddleGame.CleanUpRequired].gameSession}, is finished.")
  }

  override def postStop(): Unit = {
    super.postStop()
    this.redisClient.disconnect
  }

  private
  def gameSessionAlreadyExists (gameSession: GameSession): Boolean = this.redisClient.exists(gameSession)


  private
  def recordStartOfTheGame(atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {

    /*val historyAtStart = CompleteGamePlaySessionHistory(List(GameStartedTuple(atServerClockTime)))

    RecordingStatus(
        if (this
            .redisClient
            .hset(gameSession, "SessionHistory", write[CompleteGamePlaySessionHistory](historyAtStart)))

                s"Game ($gameSession), Started at ($atServerClockTime)"
        else
              s"Failed to record Game ($gameSession), refused by REDIS"

    )*/
    RecordingStatus (
          storeSessionHistory(gameSession,GameStartedTuple(atServerClockTime)) match {

            case f: FailedRedisSessionStatus => s"Failure: ${f.reason}"
            case OKRedisSessionStatus        => s"Game session ($gameSession), start recorded"
          })

  }

  private
  def recordThatAQuestionIsAnswered(
        atServerClockTime: Long, questionNAnswer: QuestionAnswerTuple, gameSession: GameSession): RecordingStatus = {

    val playTuple = GamePlayTuple(atServerClockTime,questionNAnswer)

    /*RecordingStatus (
      this.redisClient
      .lpush(gameSession, write[GamePlayTuple](playTuple)) match {

        case Some(n) =>
          if (n > 1) s"Game ($gameSession), Question(${questionNAnswer.questionID}:Answer(${questionNAnswer.answerID} recorded"
          else       s"Game ($gameSession), failed to record Question(${questionNAnswer.questionID}:Answer(${questionNAnswer.answerID}"
        case None    =>
          s"Failed to record Game ($gameSession), Question(${questionNAnswer.questionID}:Answer(${questionNAnswer.answerID}, refused by REDIS"
      }
    )*/

    storeSessionHistory(gameSession,GamePlayTuple(atServerClockTime,questionNAnswer)
  }

  private
  def recordAPauseOfTheGame(
        atServerClockTime: Long, gameSession: GameSession): RecordingStatus = {

    val pausedTuple = GamePausedTuple(atServerClockTime)

    RecordingStatus (
      this.redisClient.lpush(gameSession, write[GamePausedTuple](pausedTuple)) match {

        case Some(n) =>
          if (n > 1) s"Game ($gameSession), paused at ($atServerClockTime)"
          else       s"Game ($gameSession), failed to record pause at ($atServerClockTime)"
        case None    =>
          s"Failed to record Game ($gameSession), failed to record pause at ($atServerClockTime), refused by REDIS"
      }

    )
      RecordingStatus(s"Game ($gameSession), paused at $atServerClockTime")

  }

  private
  def recordEndOfTheGame(
            atServerClockTime: Long, endedHow: GameEndingReason, gameSession: GameSession): RecordingStatus = {

    val endedTuple = GameEndedTuple(atServerClockTime, endedHow.toString)

    RecordingStatus (
      this.redisClient.lpush(gameSession, write[GameEndedTuple](endedTuple))  match {

        case Some(n) =>
          if (n > 1) s"Game ($gameSession), ended at ($atServerClockTime), cause ($endedHow)"
          else       s"Game ($gameSession), failed to record end at ($atServerClockTime)"
        case None    =>
          s"Failed to record Game ($gameSession), failed to record end at ($atServerClockTime), refused by REDIS"
      }
    )
  }

  private
  def removeGameSession (gameSession: GameSession): RecordingStatus = {

    RecordingStatus(
      this.redisClient.del(gameSession) match {

        case Some(n) => s"Game ($gameSession), ($n) key deleted."
        case None    => s"Game ($gameSession), no such key found"
      }
    )
  }

  private
  def extractCurrentGamePlayRecord (gameSession: GameSession): RecordingStatus = {

    val gamePlayRecord =
      this.redisClient
      .lrange(gameSession,0,-1)
      .getOrElse(List(Some(s"No record for session ($gameSession")))
      .flatten

    RecordingStatus(s"Game so far | ${gamePlayRecord.mkString(" ** ")}")
  }

  private def
  storeSessionHistory(gameSession: GameSession, latestRecord: RedisGameInfoTuple): RedisSessionStatus = {

    val historySoFar = retrieveSessionHistory(gameSession, "SessionHistory")

    if (historySoFar == NonExistingCompleteGamePlaySessionHistory)
      log.info(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
      FailedRedisSessionStatus(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
    else {
      val updatedHistory = historySoFar.elems :+ latestRecord
      this.redisClient.hset(gameSession, "SessionHistory", CompleteGamePlaySessionHistory(updatedHistory))
      log.info(s"Game Session:Key ($gameSession:SessionHistory) updated with ($latestRecord)")
      OKRedisSessionStatus
    }
  }

  private def
  retrieveSessionHistory (gameSession: GameSession, key: String): CompleteGamePlaySessionHistory = {

    val historySoFar = this.redisClient.hget(gameSession, key)

    historySoFar match {
      case Some(record) => parse(record).extract[CompleteGamePlaySessionHistory]
      case None         => NonExistingCompleteGamePlaySessionHistory

    }
  }
}

object GamePlayRecorderActor {
  def props = Props(new GamePlayRecorderActor)
}

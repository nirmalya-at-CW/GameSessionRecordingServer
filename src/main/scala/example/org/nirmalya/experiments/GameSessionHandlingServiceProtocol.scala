package example.org.nirmalya.experiments

import java.util.UUID

import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.REQStartAGameWith
import org.json4s.{DefaultFormats, Formats, ShortTypeHints}
import org.json4s.native.Serialization

import de.heikoseeberger.akkahttpjson4s.Json4sSupport

/**
  * Created by nirmalya on 5/6/17.
  */



object GameSessionHandlingServiceProtocol {

  object ExternalAPIParams {

    case class REQStartAGameWith(companyName: String, companyID: String, manager: String, playerID: String, gameID: String, gameName: String, gameSessionUUID: String) {
      override def toString =
        new StringBuffer().append(companyID)      .append(".")
                          .append(companyName)    .append(".")
                          .append(manager)        .append(".")
                          .append(playerID)       .append(".")
                          .append(gameID)         .append(".")
                          .append(gameName)       .append(".")
                          .append(gameSessionUUID)
        .toString
    }
    case class REQSetQuizForGameWith(sessionID: String, questionMetadata: String )
    case class REQPlayAGameWith(sessionID: String, questionID: String, answerID: String, isCorrect: Boolean, points: Int, timeSpentToAnswerAtFE: Int)
    case class REQPlayAClipWith(sessionID: String, clipName: String)
    case class REQPauseAGameWith(sessionID: String)
    case class REQEndAGameWith(sessionID: String, totalTimeTakenByPlayerAtFE: Int)

    case class ExpandedMessage (successId: Int, description: String)
    case class OutcomeContent  (game_session_id: String)
    case class RESPGameSession (opSuccess: Boolean, message: ExpandedMessage, contents: OutcomeContent)
  }

  sealed trait GameSessionEndingReason
  case object  GameSessionEndedByPlayer extends GameSessionEndingReason
  case object  GameSessionEndedByTimeOut extends GameSessionEndingReason
  case object  GameSessionCreatedButNotStarted extends GameSessionEndingReason
  case object  GameSessionEndedByManager extends GameSessionEndingReason


  case class GameChosen(company: String, manager: String, playerID: String, gameName: String)
  case class QuestionAnswerTuple(questionID: Int, answerID: Int, isCorrect: Boolean, points: Int, timeTakenToAnswerAtFE: Int)

  case class GameSession(sessionID: String, playerID: String) {
    override def toString = sessionID
  }

  sealed trait GameInfoTupleInREDIS

  case class GameCreatedTupleInREDIS     (flag: String) extends GameInfoTupleInREDIS
  case class GameInitiatedTupleInREDIS   (t: Long) extends GameInfoTupleInREDIS
  case class GamePreparedTupleInREDIS    (t: Long, questionMetadata: String) extends GameInfoTupleInREDIS
  case class GamePlayedTupleInREDIS      (t: Long, questionAnswer: QuestionAnswerTuple) extends GameInfoTupleInREDIS
  case class GameClipRunInREDIS          (t: Long, clipName: String) extends GameInfoTupleInREDIS
  case class GamePausedTupleInREDIS      (t: Long) extends GameInfoTupleInREDIS
  case class GameEndedTupleInREDIS       (t: Long, gameEndingReason: String, totalTimeTakenByPlayer: Int) extends GameInfoTupleInREDIS

  case class CompleteGamePlaySessionHistory(elems: List[GameInfoTupleInREDIS])
  object NonExistingCompleteGamePlaySessionHistory extends CompleteGamePlaySessionHistory(elems = List.empty)

  implicit val formats_2 = Serialization.formats(
    ShortTypeHints(
      List(
        classOf[GameCreatedTupleInREDIS],
        classOf[GameInitiatedTupleInREDIS],
        classOf[GamePlayedTupleInREDIS],
        classOf[GameClipRunInREDIS],
        classOf[GamePausedTupleInREDIS],
        classOf[QuestionAnswerTuple],
        classOf[GameEndedTupleInREDIS],
        classOf[GamePreparedTupleInREDIS],
        classOf[GameChosen],
        classOf[REQStartAGameWith],
        classOf[RecordingStatus]
      )
    )
  )

  object HuddleGame {

    sealed trait HuddleGameFSMData
    case object DataToBeginWith extends  HuddleGameFSMData  // Not used at this point in time
    case class  DataToCleanUpRedis(gameSession: GameSession) extends HuddleGameFSMData

    sealed trait HuddleGameEvent

    case class EvCreated(gameSession: GameSession) extends HuddleGameEvent
    case class EvInitiated(startedAt: Long, gameSession: GameSession) extends  HuddleGameEvent
    case class EvQuizIsFinalized(finalizedAt: Long, questionMetadata: String, gameSession: GameSession) extends  HuddleGameEvent
    case class EvPlayingClip(beganPlayingAt: Long, clipName: String, gameSession: GameSession) extends HuddleGameEvent
    case class EvQuestionAnswered(receivedAt: Long, questionAndAnswer:QuestionAnswerTuple, gameSession: GameSession) extends HuddleGameEvent
    case class EvPaused(pausedAt: Long, gameSession: GameSession) extends HuddleGameEvent
    case class EvEnded(endedAt: Long, endedBy: GameSessionEndingReason = GameSessionEndedByPlayer, totalTimeTakenByPlayer: Int, gameSession: GameSession) extends HuddleGameEvent
    case class EvCleanUpRequired(gameSession: GameSession) extends HuddleGameEvent
    case class EvGamePlayRecordSoFarRequired(gameSession: GameSession) extends HuddleGameEvent
    case object EvGameShouldHaveStartedByNow extends HuddleGameEvent


    sealed trait HuddleGameSessionState

    case object GameSessionYetToStartState      extends HuddleGameSessionState
    case object GameSessionIsBeingPreparedState extends HuddleGameSessionState
    case object GameSessionHasStartedState      extends HuddleGameSessionState
    case object GameSessionIsContinuingState    extends HuddleGameSessionState
    case object GameSessionIsPausedState        extends HuddleGameSessionState
    case object GameSessionHasEndedState        extends HuddleGameSessionState
    case object GameSessionIsWrappingUpState    extends HuddleGameSessionState

  }

  case class RecordingStatus(details: String)

  case class EmittedWhenGameSessionIsFinished(contents: String)


  trait RedisSessionStatus
  case class  FailedRedisSessionStatus(reason: String) extends RedisSessionStatus
  case object OKRedisSessionStatus extends RedisSessionStatus
}

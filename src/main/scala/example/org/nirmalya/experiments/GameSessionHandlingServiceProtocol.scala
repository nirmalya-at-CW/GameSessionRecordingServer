package example.org.nirmalya.experiments

import java.util.UUID

import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{REQStartAGameWith, RESPGameSessionBody}
import org.json4s.{DefaultFormats, Formats, ShortTypeHints}
import org.json4s.native.Serialization
import de.heikoseeberger.akkahttpjson4s.Json4sSupport

/**
  * Created by nirmalya on 5/6/17.
  */



object GameSessionHandlingServiceProtocol {

  object ExternalAPIParams {

    case class REQStartAGameWith(companyID: String, departmentID: String, gameID: String, playerID: String,  companyName: String,  manager: String, gameName: String, gameSessionUUID: String) {
      override def toString =
        new StringBuffer().append(companyID)      .append(".")
                          .append(departmentID)   .append(".")
                          .append(gameID)         .append(".")
                          .append(playerID)       .append(".")
                          .append(companyName)    .append(".")
                          .append(manager)        .append(".")
                          .append(gameName)       .append(".")
                          .append(gameSessionUUID)
        .toString

      def jsonify = ()
    }
    case class REQSetQuizForGameWith(sessionID: String, questionMetadata: String )
    case class REQPlayAGameWith(sessionID: String, questionID: String, answerID: String, isCorrect: Boolean, points: Int, timeSpentToAnswerAtFE: Int) {}
    case class REQPlayAClipWith(sessionID: String, clipName: String)
    case class REQPauseAGameWith(sessionID: String)
    case class REQEndAGameWith(sessionID: String, totalTimeTakenByPlayerAtFE: Int)
    case class REQEndAGameByManagerWith(sessionID: String, managerName: String)

    case class ExpandedMessage (successId: Int, description: String)
    case class Supplementary(dataCarried: Map[String,String])
    case class RESPGameSessionBody(opSuccess: Boolean, message: ExpandedMessage, contents: Option[Map[String,String]]=None)
  }

  sealed trait GameSessionEndingReason
  case object  GameSessionEndedByPlayer extends GameSessionEndingReason
  case object  GameSessionEndedByTimeOut extends GameSessionEndingReason
  case object  GameSessionCreatedButNotStarted extends GameSessionEndingReason
  case object  GameSessionEndedByManager extends GameSessionEndingReason


  case class GameKey(companyID: String, departmentID: String, gameID: String, playerID: String) {
    override def toString =
      new StringBuffer().append(companyID)      .append(".")
                        .append(departmentID)   .append(".")
                        .append(gameID)         .append(".")
                        .append(playerID)
        .toString
  }
  case class QuestionAnswerTuple(questionID: Int, answerID: Int, isCorrect: Boolean, points: Int, timeTakenToAnswerAtFE: Int)

  case class GameSession(companyID: String, departmentID: String, gameID: String, playerID: String,  gameName: String, gameSessionUUID: String) {

    val gameKey = GameKey(companyID,departmentID,gameID,playerID)
    override def toString = new StringBuffer().append(gameKey.toString).append(".").append(gameSessionUUID).toString
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
        classOf[GameKey],
        classOf[REQStartAGameWith],
        classOf[RecordingStatus],
        classOf[RESPGameSessionBody]
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
    case class EvForceEndedByManager(endedAt: Long, endedBy: GameSessionEndingReason = GameSessionEndedByPlayer, managerName: String, gameSession: GameSession) extends HuddleGameEvent
    case class EvCleanUpRequired(gameSession: GameSession) extends HuddleGameEvent
    case class EvGamePlayRecordSoFarRequired(gameSession: GameSession) extends HuddleGameEvent
    case object EvGameShouldHaveStartedByNow extends HuddleGameEvent
    case object EvGameShouldHaveEndedByNow   extends HuddleGameEvent


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

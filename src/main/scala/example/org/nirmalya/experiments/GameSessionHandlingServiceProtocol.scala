package example.org.nirmalya.experiments

import java.util.UUID


import org.json4s.{DefaultFormats, Formats, ShortTypeHints}
import org.json4s.native.Serialization

/**
  * Created by nirmalya on 5/6/17.
  */



object protocol {

  sealed trait GameEndingReason
  case object  GameEndedByPlayer extends GameEndingReason
  case object  GameEndedByTimeOut extends GameEndingReason


  case class GameChosen(company: String, manager: String, playerID: String, gameName: String)
  case class QuestionAnswerTuple(questionID: Int, answerID: Int, isCorrect: Boolean, points: Int)

  case class GameSession(sessionID: String, playerID: String)

  sealed trait GameInfoTupleInREDIS

  case class GameCreatedTupleInREDIS  (flag: String) extends GameInfoTupleInREDIS
  case class GameStartedTupleInREDIS  (t: Long) extends GameInfoTupleInREDIS
  case class GamePlayTupleInREDIS     (t: Long, questionAnswer: QuestionAnswerTuple) extends GameInfoTupleInREDIS
  case class GamePausedTupleInREDIS   (t: Long) extends GameInfoTupleInREDIS
  case class GameEndedTupleInREDIS    (t:Long, gameEndingReason: String) extends GameInfoTupleInREDIS

  case class CompleteGamePlaySessionHistory(elems: List[GameInfoTupleInREDIS])
  object NonExistingCompleteGamePlaySessionHistory extends CompleteGamePlaySessionHistory(elems = List.empty)

  implicit val formats_2 = Serialization.formats(
    ShortTypeHints(
      List(
        classOf[GameCreatedTupleInREDIS],
        classOf[GameStartedTupleInREDIS],
        classOf[GamePlayTupleInREDIS],
        classOf[GamePausedTupleInREDIS],
        classOf[QuestionAnswerTuple],
        classOf[GameEndedTupleInREDIS]
      )
    )
  )

  object HuddleGame {

    sealed trait HuddleGameFSMData
    case object DataToBeginWith extends  HuddleGameFSMData  // Not used at this point in time
    case class  DataToCleanUpRedis(gameSession: GameSession) extends HuddleGameFSMData

    sealed trait HuddleGameEvent

    case class EvCreated(gameSession: GameSession) extends HuddleGameEvent
    case class EvStarted(startedAt: Long, gameSession: GameSession) extends  HuddleGameEvent
    case class EvQuestionAnswered(receivedAt: Long, questionAndAnswer:QuestionAnswerTuple, gameSession: GameSession) extends HuddleGameEvent
    case class EvPaused(pausedAt: Long, gameSession: GameSession) extends HuddleGameEvent
    case class EvEnded(endedAt: Long, endedBy: GameEndingReason = GameEndedByPlayer, gameSession: GameSession) extends HuddleGameEvent
    case class EvCleanUpRequired(gameSession: GameSession) extends HuddleGameEvent
    case class EvGamePlayRecordSoFarRequired(gameSession: GameSession) extends HuddleGameEvent


    sealed trait HuddleGameState

    case object GameYetToStartState   extends HuddleGameState
    case object GameHasStartedState   extends HuddleGameState
    case object GameIsContinuingState extends HuddleGameState
    case object GameIsPausedState     extends HuddleGameState
    case object GameHasEndedState     extends HuddleGameState
    case object GameIsWrappingUpState extends HuddleGameState

  }

  case class RecordingStatus(details: String)


  trait RedisSessionStatus
  case class  FailedRedisSessionStatus(reason: String) extends RedisSessionStatus
  case object OKRedisSessionStatus extends RedisSessionStatus
}

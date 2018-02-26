package com.OneHuddle.GamePlaySessionService

import java.time.{LocalDateTime, ZonedDateTime}

import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams.{HuddleRESPGameSessionBody, REQStartAGameWith}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.MultiPlayerGameResult.MultiPlayerGameResult
import org.json4s.{DefaultFormats, Formats, ShortTypeHints}
import org.json4s.native.Serialization
import de.heikoseeberger.akkahttpjson4s.Json4sSupport

/**
  * Created by nirmalya on 5/6/17.
  */



object GameSessionHandlingServiceProtocol {

  object MultiPlayerGameResult extends Enumeration {
    type MultiPlayerGameResult = Value
    val LOST, WON = Value
  }

  object ExternalAPIParams {

    case class REQStartAGameWith(
                  companyID: String, departmentID: String, gameID: String,
                  playerID: String,  gameType: String, groupID: Option[String] = None,
                  gameName: String, gameSessionUUID: String, playedInTimezone: String) {
      override def toString =
        new StringBuffer().append(companyID)      .append(".")
                          .append(departmentID)   .append(".")
                          .append(gameID)         .append(".")
                          .append(playerID)       .append(".")
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

    sealed trait HuddleRESPGameSessionBody { val opSuccess: Boolean }
    case class   HuddleRESPGameSessionBodyWhenSuccessful(
                    message: ExpandedMessage, contents: Option[Map[String,String]]=None, opSuccess: Boolean = true)
                 extends HuddleRESPGameSessionBody
    case class   HuddleRESPGameSessionBodyWhenFailed(
                   message: ExpandedMessage, contents: Option[Map[String,String]]=None, opSuccess: Boolean = false)
                 extends HuddleRESPGameSessionBody
    case class   HuddleRESPLeaderboardSnapshotsReceivedAlright(
                   message: ExpandedMessage, contents: Option[Map[String,String]]=None, opSuccess: Boolean = false)
                 extends HuddleRESPGameSessionBody
  }

  sealed trait GameSessionEndingReason
  case object  GameSessionEndedByPlayer extends GameSessionEndingReason
  case object  GameSessionEndedByTimeOut extends GameSessionEndingReason
  case object  GameSessionCreatedButNotStarted extends GameSessionEndingReason
  case object  GameSessionEndedByManager extends GameSessionEndingReason


  case class GameSessionCompositeID(companyID: String, departmentID: String, gameID: String, playerID: String) {
    override def toString =
      new StringBuffer().append(companyID)      .append(".")
                        .append(departmentID)   .append(".")
                        .append(gameID)         .append(".")
                        .append(playerID)
        .toString
  }
  case class QuestionAnswerTuple(questionID: Int, answerID: Int, isCorrect: Boolean, points: Int, timeTakenToAnswerAtFE: Int)

  case class GameSession(companyID: String, departmentID: String, gameID: String, playerID: String,
                         gameName: String, gameSessionUUID: String, groupID: String = "NOTSET", gameType: String = "SP",
                         playedInTimezone: String
                        ) {

    val gameSessionKey = gameSessionUUID
    override def toString = new StringBuffer().append(companyID)      .append(".")
                                              .append(departmentID)   .append(".")
                                              .append(gameID)         .append(".")
                                              .append(gameType)       .append(".")
                                              .append(playerID)       .append(".")
                                              .append(gameSessionUUID)
                                              .toString
  }


  trait ComputedGameSession {
    val companyID: String
    val departmentID: String
    val playerID: String
    val gameID: String
    val gameType: String
    val gameSessionUUID: String
    val groupID: String
    val startedAt: ZonedDateTime
    val finishedAt: ZonedDateTime
    val timezoneApplicable: String
    val totalPointsObtained: Int
    val timeTakenToFinish: Int
    val endedBecauseOf: String

  }
  case class ComputedGameSessionRegSP(
               companyID: String,departmentID: String, playerID: String, gameID: String,
               gameSessionUUID: String, groupID: String,
               startedAt: ZonedDateTime, finishedAt: ZonedDateTime, timezoneApplicable: String,
               totalPointsObtained: Int, timeTakenToFinish: Int, endedBecauseOf: String
             ) extends ComputedGameSession {
    override val gameType = "REGSP"
  }

  case class ComputedGameSessionRegMP(
               companyID: String,departmentID: String, playerID: String, gameID: String,
               gameSessionUUID: String, groupID: String,
               startedAt: ZonedDateTime, finishedAt: ZonedDateTime, timezoneApplicable: String,
               totalPointsObtained: Int, timeTakenToFinish: Int, statusWinOrLose: MultiPlayerGameResult,
               endedBecauseOf: String
             ) extends ComputedGameSession {
    override val gameType = "REGMP"
  }

  case class ComputedGameSessionMiniSP(
               companyID: String,departmentID: String, playerID: String, gameID: String,
               gameSessionUUID: String, groupID: String,
               startedAt: ZonedDateTime, finishedAt: ZonedDateTime, timezoneApplicable: String,
               totalPointsObtained: Int, timeTakenToFinish: Int, endedBecauseOf: String
             ) extends ComputedGameSession {
    override val gameType = "MINISP"
  }

  case class ComputedGameSessionMiniMP(
               companyID: String,departmentID: String, playerID: String, gameID: String,
               gameSessionUUID: String, groupID: String,
               startedAt: ZonedDateTime, finishedAt: ZonedDateTime, timezoneApplicable: String,
               totalPointsObtained: Int, timeTakenToFinish: Int, statusWinOrLose: MultiPlayerGameResult,
               endedBecauseOf: String
             ) extends ComputedGameSession {
    override val gameType = "MINIMP"
  }

  case class LeaderBoardConsumableData(
               companyID: String, departmentID: String, gameID: String, playerID: String,
               groupID: String, gameSessionUUID: String, score: Int
             )

  case class LeaderBoardSnapshot(takenAt: ZonedDateTime, timezoneApplicable: String,
                                 companyID: String, departmentID: String, gameID: String, playerID: String,
                                 gameType: String, groupID: String, rankComputed: Int
             )

  case class LeaderBoardSnapshotBunch(bunch: List[LeaderBoardSnapshot])

  trait PlayerPerformancePerLastSession {
    val companyID: String
    val belongsToDepartment: String
    val playerID: String
    val gameID: String
    val gameType: String
    val groupID: String
    val pointsObtained: Int
    val timeTaken: Int
    val winsAchieved: Int
  }

  case class PlayerPerformanceRecordSP(
                companyID: String, belongsToDepartment: String, playerID: String, gameID: String, groupID: String = "NOTSET",
                lastPlayedOn: ZonedDateTime, timezoneApplicable: String, pointsObtained: Int, timeTaken: Int)
             extends PlayerPerformancePerLastSession {
            override val winsAchieved = 0
            override val gameType = "REGSP"

  }
  case class PlayerPerformanceRecordMP(
                companyID: String, belongsToDepartment: String, playerID: String, gameID: String, groupID: String = "NOTSET",
                lastPlayedOn: ZonedDateTime, timezoneApplicable: String, pointsObtained: Int, timeTaken: Int, winsAchieved: Int)
             extends PlayerPerformancePerLastSession {

             override val gameType = "REGMP"
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

  // As required by json4s library that we are using for (un)jsonification operations
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
        classOf[GameSessionCompositeID],
        classOf[REQStartAGameWith],
        classOf[RedisRecordingStatus],
        classOf[HuddleRESPGameSessionBody],
        classOf[LeaderBoardConsumableData],
        classOf[LeaderBoardSnapshotBunch]
      )
    )
  )

  object HuddleGame {

    sealed trait HuddleGameFSMData
    case object UninitializedDataToBeginWith extends  HuddleGameFSMData  // Not used at this point in time
    case class  DataToContinueWith(sessionBeganAt: Long) extends  HuddleGameFSMData
    case class  DataToEndWith(sessionBeganAt: Long, sessionEndedAt: Long) extends HuddleGameFSMData

    sealed trait HuddleGameSessionTerminationEvent { val reasonWhySessionEnds: GameSessionEndingReason }
    sealed trait HuddleGameEvent

    case class   EvCreated(gameSession: GameSession) extends HuddleGameEvent
    case class   EvInitiated(startedAt: Long) extends  HuddleGameEvent
    case class   EvQuizIsFinalized(finalizedAt: Long, questionMetadata: String ) extends  HuddleGameEvent
    case class   EvPlayingClip(beganPlayingAt: Long, clipName: String) extends HuddleGameEvent
    case class   EvQuestionAnswered(receivedAt: Long, questionAndAnswer:QuestionAnswerTuple) extends HuddleGameEvent
    case class   EvPaused(pausedAt: Long) extends HuddleGameEvent
    case class   EvEndedByPlayer(endedAt: Long,totalTimeTakenByPlayer: Int) extends HuddleGameEvent with HuddleGameSessionTerminationEvent {
      val reasonWhySessionEnds = GameSessionEndedByPlayer
    }
    case class   EvForceEndedByManager(endedAt: Long,  managerName: String) extends HuddleGameEvent with HuddleGameSessionTerminationEvent {
      val reasonWhySessionEnds = GameSessionEndedByManager
    }

    case class   EvEndedByTimeout (endedAt: Long) extends HuddleGameEvent with HuddleGameSessionTerminationEvent {
      val reasonWhySessionEnds = GameSessionEndedByTimeOut
    }
    case class   EvSessionCleanupIndicated(endedAt: Long, endingReason: GameSessionEndingReason) extends HuddleGameEvent
    case object  EvGamePlayRecordSoFarRequired extends HuddleGameEvent
    case object  EvGameShouldHaveStartedByNow extends HuddleGameEvent
    case object  EvGameShouldHaveEndedByNow   extends HuddleGameEvent
    case class   EvGameFinishedAndScored (computedGameSession: ComputedGameSessionRegSP) extends HuddleGameEvent
    case object  EvGameSessionSaved extends HuddleGameEvent
    case object  EvGameSessionTerminationIndicated extends HuddleGameEvent
    case object  EvStateHolderIsGoingDown extends HuddleGameEvent


    sealed trait HuddleGameSessionState

    case object GameSessionYetToStartState                  extends HuddleGameSessionState
    case object GameSessionIsBeingPreparedState             extends HuddleGameSessionState
    case object GameSessionHasStartedState                  extends HuddleGameSessionState
    case object GameSessionIsContinuingState                extends HuddleGameSessionState
    case object GameSessionIsPausedState                    extends HuddleGameSessionState
    case object GameSessionHasEndedState                    extends HuddleGameSessionState
    case object GameSessionIsWrappingUpState                extends HuddleGameSessionState
    case object GameSessionIsWaitingForInstructionToClose   extends HuddleGameSessionState

  }

  object EmittedEvents {

    sealed trait EventSubscribedByAdminPanel
      case class DataSharedWithAdminPanel(
                       companyID: String, belongsToDepartment: String,
                       playerID: String, gameSessionUUID: String
                     )

    case class   EvGameSessionLaunched (data: DataSharedWithAdminPanel) extends EventSubscribedByAdminPanel
    case class   EvGameSessionFinishedByPlayer(data: DataSharedWithAdminPanel) extends EventSubscribedByAdminPanel
    case class   EvGameSessionTerminatedByTimeOut(data: DataSharedWithAdminPanel) extends EventSubscribedByAdminPanel
    case class   EvGameSessionTerminatedByManager(data: DataSharedWithAdminPanel, manager: String) extends EventSubscribedByAdminPanel
  }

  case class  RedisRecordingStatus(details: String)

  case class  EmittedWhenGameSessionIsFinished(contents: String)
  case class  AckOfDepatchToLeaderBoard(
                 val statusHTTP: Int, val statusTextHTTP: String, val errorReason: Option[String] = None
             )

  trait RedisSessionStatus
  case class  FailedRedisSessionStatus(reason: String) extends RedisSessionStatus
  case object OKRedisSessionStatus extends RedisSessionStatus

  object DBHatch {

    case class DBActionGameSessionRecord(
          companyID: String, belongsToDepartment: String, playerID: String, gameID: String,
          gameSessionUUID: String, belongsToGroup: String, gameType: String = "REGSP", gameName: String = "NOTSUPPLIED",
          startedAtInUTC: LocalDateTime, finishedAtInUTC: LocalDateTime, timezoneApplicable: String, endReason: String,
          score: Int, timeTaken: Int, outcomeInMPGameSession: String = "NOTAPPLICABLE")


    case class DBActionPlayerPerformanceRecord(
          companyID: String, belongsToDepartment: String, playerID: String, gameID: String, gameType: String, groupID: String,
          lastPlayedOn: LocalDateTime, timezoneApplicable: String,
          pointsObtained: Int, timeTaken: Int, winsAchieved: Int)

    case class DBActionLeaderBoardSnapshotRecord(
          takenAtInUTC: LocalDateTime, timezoneApplicable: String,
          companyID: String, belongsToDepartment: String, playerID: String, gameID: String, gameType: String, groupID: String,
          rankComputed: Int)
    
    sealed trait DBAction
    case class   DBActionInsert(r:DBActionGameSessionRecord) extends DBAction
    
    sealed trait DBActionOutcome
    case class   DBActionInsertSuccess(i: Int)       extends DBActionOutcome

    case class   DBActionInsertFailure(s: String)    extends DBActionOutcome
  }
}

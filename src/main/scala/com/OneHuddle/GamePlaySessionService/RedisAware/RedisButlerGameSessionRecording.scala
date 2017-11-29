package com.OneHuddle.GamePlaySessionService.RedisAware

import akka.actor.{Actor, ActorLogging, Props}

import com.redis.RedisClient
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{CompleteGamePlaySessionHistory, FailedRedisSessionStatus, GameClipRunInREDIS, GameCreatedTupleInREDIS, GameEndedTupleInREDIS, GameInfoTupleInREDIS, GameInitiatedTupleInREDIS, GamePausedTupleInREDIS, GamePlayedTupleInREDIS, GamePreparedTupleInREDIS, GameSession, GameSessionEndingReason, HuddleGame, NonExistingCompleteGamePlaySessionHistory, OKRedisSessionStatus, QuestionAnswerTuple, RedisRecordingStatus, RedisSessionStatus, formats_2}

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory


import scala.util.{Failure, Success}


/**
  * Created by nirmalya on 28/6/17.
  */
class RedisButlerGameSessionRecording(redisHost: String, redisPort: Int) {

  //TODO
  // 1) Pick Timeout value from Akka config, instead of hardcoding
  // 2) We need to manage the connection pool of REDIS. Important.

  val redisClient = new RedisClient(redisHost, redisPort)
  val logger = LoggerFactory.getLogger(classOf[RedisButlerGameSessionRecording])

  def
  releaseConnection = redisClient.disconnect

  def
  retrieveSessionHistory (gameSession: GameSession, key: String): CompleteGamePlaySessionHistory = {

    val historySoFar = this.redisClient.hget(gameSession, key)

    // println(historySoFar)

    historySoFar match {
      case Some(record) => parse(record).extract[CompleteGamePlaySessionHistory]
      case None         => NonExistingCompleteGamePlaySessionHistory

    }
  }

  def
  storeSessionHistory(gameSession: GameSession, latestRecord: GameInfoTupleInREDIS): RedisSessionStatus = {

    val historySoFar = retrieveSessionHistory(gameSession, "SessionHistory")

    if (historySoFar == NonExistingCompleteGamePlaySessionHistory) {
      logger.info(s"Game Session:Key ($gameSession:SessionHistory) is not found.")
      FailedRedisSessionStatus(s"($gameSession:SessionHistory) not found.")
    }
    else {
      val updatedHistory = historySoFar.elems :+ latestRecord
      val completeSessionSoFar = write[CompleteGamePlaySessionHistory](CompleteGamePlaySessionHistory(updatedHistory))
      this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)
      logger.debug(s"($gameSession:SessionHistory) updated with ($latestRecord)")
      OKRedisSessionStatus
    }
  }

  def
  extractCurrentGamePlayRecord (gameSession: GameSession): RedisRecordingStatus = {

    val gamePlayRecord = this.redisClient.hget(gameSession, "SessionHistory")

    RedisRecordingStatus(
      gamePlayRecord match {

        case Some(retrievedRec)  =>
          logger.debug(s"Success, op: Extraction, Game ($gameSession)")
          retrievedRec
        case None     =>
          logger.debug(s"Failure, op: Extraction, GamePlayRecord for ($gameSession) not found")
          ("Not Found")
      }
    )
  }
  def
  removeGameSessionFromREDIS (gameSession: GameSession): RedisRecordingStatus = {

    RedisRecordingStatus(
      this.redisClient.del(gameSession) match {

        case Some(n) =>
          logger.debug(s"Success, op: Removal, Game ($gameSession), ($n) key removed")
          "Removed"
        case None    =>
          logger.debug(s"Failure, op: Removal, Game ($gameSession), no such key found")
          "Not Removed"
      }
    )
  }

  def
  recordEndOfTheGame(
        atServerClockTime: Long, endedHow: GameSessionEndingReason, totalTimeTakenByPlayer: Int, gameSession: GameSession
  ): RedisRecordingStatus = {
    RedisRecordingStatus (
      storeSessionHistory(gameSession,GameEndedTupleInREDIS(atServerClockTime, endedHow.toString,totalTimeTakenByPlayer )) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: ${f.reason}, op: Ended, sessionID($gameSession), how(${endedHow.toString})")
          "Not Ended"
        case OKRedisSessionStatus        =>
          logger.debug(s"Success, op: Ended, sessionID($gameSession), how(${endedHow.toString})")
          "Ended"
      }
    )
  }

  def recordAPauseOfTheGame(
        atServerClockTime: Long, gameSession: GameSession): RedisRecordingStatus = {
    RedisRecordingStatus (
      storeSessionHistory(gameSession,GamePausedTupleInREDIS(atServerClockTime)) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: ${f.reason}, op: Paused, sessionID($gameSession)")
          ("Not Paused")
        case OKRedisSessionStatus        =>
          logger.debug(s"Success: op: Paused, sessionID($gameSession)")
          ("Paused")
      }
    )
  }

  def
  recordThatAQuestionIsAnswered(
        atServerClockTime: Long, questionNAnswer: QuestionAnswerTuple, gameSession: GameSession): RedisRecordingStatus = {

    // val playTuple = GamePlayTuple(atServerClockTime,questionNAnswer)

    RedisRecordingStatus (
      storeSessionHistory(gameSession,GamePlayedTupleInREDIS(atServerClockTime,questionNAnswer)) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: ${f.reason}, op: QuestionAnswered, sessionID($gameSession), question(${questionNAnswer.questionID},${questionNAnswer.answerID}")
          ("Not QuestionAnswered")
        case OKRedisSessionStatus        =>
          logger.debug(s"Sucess: op: QuestionAnswered, sessionID($gameSession), Played(Q:${questionNAnswer.questionID},A:${questionNAnswer.answerID})")
          ("QuestionAnswered")
      }
    )
  }

  def recordThatClipIsPlayed(atServerClockTime: Long, clipName: String, gameSession: GameSession): RedisRecordingStatus = {

    RedisRecordingStatus (
      storeSessionHistory(gameSession,GameClipRunInREDIS(atServerClockTime,clipName)) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: ${f.reason}, op: recordClipPlayed, sessionID($gameSession), clip(${clipName}")
          ("Not Clip Played")
        case OKRedisSessionStatus        =>
          logger.debug(s"Success, Op: recordClipPlayed, sessionID($gameSession)")
          ("Clip Played")
      }
    )
  }

  def
  recordInitiationOfTheGame(atServerClockTime: Long, gameSession: GameSession): RedisRecordingStatus = {

    // TODO: Handle this during transition from YetToStart to Started
    // val createdTuple = write[GameCreatedTuple](GameCreatedTuple("Game Sentinel"))
    val completeSessionSoFar = write[CompleteGamePlaySessionHistory](
      CompleteGamePlaySessionHistory(
        List(GameCreatedTupleInREDIS("Game Sentinel")))
    )
    this.redisClient.hset(gameSession, "SessionHistory", completeSessionSoFar)

    RedisRecordingStatus (
      storeSessionHistory(gameSession,GameInitiatedTupleInREDIS(atServerClockTime)) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: (${f.reason}), Op: recordInitiation, sessionID($gameSession)")
          ("Not Initiated")
        case OKRedisSessionStatus        =>
          logger.debug(s"Success, Op: recordInitiation, sessionID($gameSession)")
          ("Initiated")
      })

  }

  def
  recordPreparationOfTheGame (atServerClockTime: Long, questionMetadata: String, gameSession: GameSession): RedisRecordingStatus = {

    RedisRecordingStatus (
      storeSessionHistory(gameSession,GamePreparedTupleInREDIS(atServerClockTime,questionMetadata)) match {

        case f: FailedRedisSessionStatus =>
          logger.debug(s"Failure: (${f.reason}), Op: recordPreparation, sessionID($gameSession), Metadata:(${questionMetadata})")
          ("Not Prepared")
        case OKRedisSessionStatus        =>
          logger.debug(s"Success, Op: recordPreparation, sessionID($gameSession), Quiz set up (${questionMetadata})")
          ("Prepared")
      }
    )
  }

  private
  def gameSessionAlreadyExists (gameSession: GameSession): Boolean = this.redisClient.exists(gameSession)

}


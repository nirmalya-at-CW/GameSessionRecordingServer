/*
 * This file is generated by jOOQ.
*/
package com.OneHuddle.GamePlaySessionService.jOOQ.generated.tables.records


import com.OneHuddle.GamePlaySessionService.jOOQ.generated.tables.Gamesessionrecords

import java.lang.Integer
import java.lang.String
import java.sql.Timestamp

import javax.annotation.Generated

import org.jooq.Field
import org.jooq.Record13
import org.jooq.Record5
import org.jooq.Row13
import org.jooq.impl.UpdatableRecordImpl

import scala.Array


/**
 * This class is generated by jOOQ.
 */
@Generated(
  value = Array(
    "http://www.jooq.org",
    "jOOQ version:3.10.0"
  ),
  comments = "This class is generated by jOOQ"
)
class GamesessionrecordsRecord extends UpdatableRecordImpl[GamesessionrecordsRecord](Gamesessionrecords.GAMESESSIONRECORDS) with Record13[String, String, String, String, String, String, String, String, Timestamp, String, String, Integer, Integer] {

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.companyID</code>.
   */
  def setCompanyid(value : String) : Unit = {
    set(0, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.companyID</code>.
   */
  def getCompanyid : String = {
    val r = get(0)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.belongsToDepartment</code>.
   */
  def setBelongstodepartment(value : String) : Unit = {
    set(1, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.belongsToDepartment</code>.
   */
  def getBelongstodepartment : String = {
    val r = get(1)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.playerID</code>.
   */
  def setPlayerid(value : String) : Unit = {
    set(2, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.playerID</code>.
   */
  def getPlayerid : String = {
    val r = get(2)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.gameID</code>.
   */
  def setGameid(value : String) : Unit = {
    set(3, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.gameID</code>.
   */
  def getGameid : String = {
    val r = get(3)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.gameSessionUUID</code>.
   */
  def setGamesessionuuid(value : String) : Unit = {
    set(4, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.gameSessionUUID</code>.
   */
  def getGamesessionuuid : String = {
    val r = get(4)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.belongsToGroup</code>.
   */
  def setBelongstogroup(value : String) : Unit = {
    set(5, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.belongsToGroup</code>.
   */
  def getBelongstogroup : String = {
    val r = get(5)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.gameType</code>.
   */
  def setGametype(value : String) : Unit = {
    set(6, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.gameType</code>.
   */
  def getGametype : String = {
    val r = get(6)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.gameName</code>.
   */
  def setGamename(value : String) : Unit = {
    set(7, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.gameName</code>.
   */
  def getGamename : String = {
    val r = get(7)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.lastPlayedOnInUTC</code>.
   */
  def setLastplayedoninutc(value : Timestamp) : Unit = {
    set(8, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.lastPlayedOnInUTC</code>.
   */
  def getLastplayedoninutc : Timestamp = {
    val r = get(8)
    if (r == null) null else r.asInstanceOf[Timestamp]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.timezoneApplicable</code>.
   */
  def setTimezoneapplicable(value : String) : Unit = {
    set(9, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.timezoneApplicable</code>.
   */
  def getTimezoneapplicable : String = {
    val r = get(9)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.endReason</code>.
   */
  def setEndreason(value : String) : Unit = {
    set(10, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.endReason</code>.
   */
  def getEndreason : String = {
    val r = get(10)
    if (r == null) null else r.asInstanceOf[String]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.score</code>.
   */
  def setScore(value : Integer) : Unit = {
    set(11, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.score</code>.
   */
  def getScore : Integer = {
    val r = get(11)
    if (r == null) null else r.asInstanceOf[Integer]
  }

  /**
   * Setter for <code>OneHuddle.GameSessionRecords.timeTaken</code>.
   */
  def setTimetaken(value : Integer) : Unit = {
    set(12, value)
  }

  /**
   * Getter for <code>OneHuddle.GameSessionRecords.timeTaken</code>.
   */
  def getTimetaken : Integer = {
    val r = get(12)
    if (r == null) null else r.asInstanceOf[Integer]
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------
  override def key : Record5[String, String, String, String, String] = {
    return super.key.asInstanceOf[ Record5[String, String, String, String, String] ]
  }

  // -------------------------------------------------------------------------
  // Record13 type implementation
  // -------------------------------------------------------------------------

  override def fieldsRow : Row13[String, String, String, String, String, String, String, String, Timestamp, String, String, Integer, Integer] = {
    super.fieldsRow.asInstanceOf[ Row13[String, String, String, String, String, String, String, String, Timestamp, String, String, Integer, Integer] ]
  }

  override def valuesRow : Row13[String, String, String, String, String, String, String, String, Timestamp, String, String, Integer, Integer] = {
    super.valuesRow.asInstanceOf[ Row13[String, String, String, String, String, String, String, String, Timestamp, String, String, Integer, Integer] ]
  }
  override def field1 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.COMPANYID
  override def field2 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.BELONGSTODEPARTMENT
  override def field3 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.PLAYERID
  override def field4 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.GAMEID
  override def field5 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.GAMESESSIONUUID
  override def field6 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.BELONGSTOGROUP
  override def field7 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.GAMETYPE
  override def field8 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.GAMENAME
  override def field9 : Field[Timestamp] = Gamesessionrecords.GAMESESSIONRECORDS.LASTPLAYEDONINUTC
  override def field10 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.TIMEZONEAPPLICABLE
  override def field11 : Field[String] = Gamesessionrecords.GAMESESSIONRECORDS.ENDREASON
  override def field12 : Field[Integer] = Gamesessionrecords.GAMESESSIONRECORDS.SCORE
  override def field13 : Field[Integer] = Gamesessionrecords.GAMESESSIONRECORDS.TIMETAKEN
  override def component1 : String = getCompanyid
  override def component2 : String = getBelongstodepartment
  override def component3 : String = getPlayerid
  override def component4 : String = getGameid
  override def component5 : String = getGamesessionuuid
  override def component6 : String = getBelongstogroup
  override def component7 : String = getGametype
  override def component8 : String = getGamename
  override def component9 : Timestamp = getLastplayedoninutc
  override def component10 : String = getTimezoneapplicable
  override def component11 : String = getEndreason
  override def component12 : Integer = getScore
  override def component13 : Integer = getTimetaken
  override def value1 : String = getCompanyid
  override def value2 : String = getBelongstodepartment
  override def value3 : String = getPlayerid
  override def value4 : String = getGameid
  override def value5 : String = getGamesessionuuid
  override def value6 : String = getBelongstogroup
  override def value7 : String = getGametype
  override def value8 : String = getGamename
  override def value9 : Timestamp = getLastplayedoninutc
  override def value10 : String = getTimezoneapplicable
  override def value11 : String = getEndreason
  override def value12 : Integer = getScore
  override def value13 : Integer = getTimetaken

  override def value1(value : String) : GamesessionrecordsRecord = {
    setCompanyid(value)
    this
  }

  override def value2(value : String) : GamesessionrecordsRecord = {
    setBelongstodepartment(value)
    this
  }

  override def value3(value : String) : GamesessionrecordsRecord = {
    setPlayerid(value)
    this
  }

  override def value4(value : String) : GamesessionrecordsRecord = {
    setGameid(value)
    this
  }

  override def value5(value : String) : GamesessionrecordsRecord = {
    setGamesessionuuid(value)
    this
  }

  override def value6(value : String) : GamesessionrecordsRecord = {
    setBelongstogroup(value)
    this
  }

  override def value7(value : String) : GamesessionrecordsRecord = {
    setGametype(value)
    this
  }

  override def value8(value : String) : GamesessionrecordsRecord = {
    setGamename(value)
    this
  }

  override def value9(value : Timestamp) : GamesessionrecordsRecord = {
    setLastplayedoninutc(value)
    this
  }

  override def value10(value : String) : GamesessionrecordsRecord = {
    setTimezoneapplicable(value)
    this
  }

  override def value11(value : String) : GamesessionrecordsRecord = {
    setEndreason(value)
    this
  }

  override def value12(value : Integer) : GamesessionrecordsRecord = {
    setScore(value)
    this
  }

  override def value13(value : Integer) : GamesessionrecordsRecord = {
    setTimetaken(value)
    this
  }

  override def values(value1 : String, value2 : String, value3 : String, value4 : String, value5 : String, value6 : String, value7 : String, value8 : String, value9 : Timestamp, value10 : String, value11 : String, value12 : Integer, value13 : Integer) : GamesessionrecordsRecord = {
    this.value1(value1)
    this.value2(value2)
    this.value3(value3)
    this.value4(value4)
    this.value5(value5)
    this.value6(value6)
    this.value7(value7)
    this.value8(value8)
    this.value9(value9)
    this.value10(value10)
    this.value11(value11)
    this.value12(value12)
    this.value13(value13)
    this
  }

  /**
   * Create a detached, initialised GamesessionrecordsRecord
   */
  def this(companyid : String, belongstodepartment : String, playerid : String, gameid : String, gamesessionuuid : String, belongstogroup : String, gametype : String, gamename : String, lastplayedoninutc : Timestamp, timezoneapplicable : String, endreason : String, score : Integer, timetaken : Integer) = {
    this()

    set(0, companyid)
    set(1, belongstodepartment)
    set(2, playerid)
    set(3, gameid)
    set(4, gamesessionuuid)
    set(5, belongstogroup)
    set(6, gametype)
    set(7, gamename)
    set(8, lastplayedoninutc)
    set(9, timezoneapplicable)
    set(10, endreason)
    set(11, score)
    set(12, timetaken)
  }
}

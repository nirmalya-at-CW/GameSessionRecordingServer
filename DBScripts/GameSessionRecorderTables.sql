
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `DateTimExperiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DateTimExperiment` (
  `justID` smallint(6) NOT NULL,
  `lastPlayedOn` datetime NOT NULL,
  PRIMARY KEY (`justID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `GameSessionRecords`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GameSessionRecords` (
  `companyID` varchar(16) COLLATE utf8_bin NOT NULL,
  `belongsToDepartment` varchar(28) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `playerID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameSessionUUID` varchar(48) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `belongsToGroup` varchar(28) COLLATE utf8_bin DEFAULT 'NOTSET',
  `gameType` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameName` varchar(16) COLLATE utf8_bin DEFAULT 'NOTSET',
  `startedAtInUTC` datetime NOT NULL,
  `finishedAtInUTC` datetime NOT NULL,
  `timezoneApplicable` varchar(16) COLLATE utf8_bin DEFAULT 'NOTSET',
  `endReason` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `score` int(11) DEFAULT '-1',
  `timeTaken` int(11) NOT NULL DEFAULT '-1',
  `outcomeInMPGameSession` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTAPPLICABLE',
  PRIMARY KEY (`companyID`,`belongsToDepartment`,`playerID`,`gameID`,`gameSessionUUID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `LiveBoardSnapshots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `LiveBoardSnapshots` (
  `recordID` int(12) NOT NULL AUTO_INCREMENT,
  `snapshotTakenAt` datetime NOT NULL,
  `timezoneApplicable` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `companyID` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `belongsToDepartment` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `playerID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameType` varchar(8) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `groupID` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `rankComputed` int(4) DEFAULT NULL,
  PRIMARY KEY (`recordID`,`snapshotTakenAt`,`timezoneApplicable`,`companyID`,`belongsToDepartment`,`playerID`,`gameID`,`gameType`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `PlayerDetails`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PlayerDetails` (
  `companyID` varchar(16) COLLATE utf8_bin NOT NULL,
  `companyName` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `belongsToDepartment` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `playerID` varchar(16) COLLATE utf8_bin NOT NULL,
  `playerName` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `playerEMailID` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `applicableTimeZone` varchar(16) COLLATE utf8_bin DEFAULT NULL,
  `belongsToGroup` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  PRIMARY KEY (`companyID`,`belongsToDepartment`,`playerID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `PlayerPerformance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PlayerPerformance` (
  `recordID` int(12) NOT NULL AUTO_INCREMENT,
  `companyID` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `belongsToDepartment` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `playerID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `gameType` varchar(8) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `groupID` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `lastPlayedOn` datetime NOT NULL,
  `timezoneApplicable` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
  `pointsObtained` int(11) NOT NULL,
  `timeTaken` int(11) NOT NULL,
  `winsAchieved` int(11) NOT NULL,
  PRIMARY KEY (`recordID`,`companyID`,`belongsToDepartment`,`playerID`,`gameID`,`gameType`,`groupID`,`lastPlayedOn`,`timezoneApplicable`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


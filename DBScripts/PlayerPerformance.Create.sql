DROP TABLE IF EXISTS `PlayerPerformance`;
CREATE TABLE `PlayerPerformance` (
	  `recordID` int(12) NOT NULL AUTO_INCREMENT,
	  `companyID` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `belongsToDepartment` varchar(32) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `playerID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `gameID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `gameType` varchar(8) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `lastPlayedOn` datetime NOT NULL,
	  `timezoneApplicable` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `pointsObtained` int(11) NOT NULL,
	  `timeTaken` int(11) NOT NULL,
	  `winsAchieved` int(11) NOT NULL DEFAULT '0',
	  PRIMARY KEY (`recordID`,`companyID`,`belongsToDepartment`,`playerID`,`gameID`,`gameType`,`lastPlayedOn`,`timezoneApplicable`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;


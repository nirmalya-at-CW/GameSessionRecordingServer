DROP TABLE IF EXISTS `GameSessionRecords`;
CREATE TABLE `GameSessionRecords` (
	  `companyID` varchar(16) COLLATE utf8_bin NOT NULL,
	  `belongsToDepartment` varchar(28) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `playerID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `gameID` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `gameSessionUUID` varchar(48) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `belongsToGroup` varchar(28) COLLATE utf8_bin DEFAULT 'NOTSET',
	  `gameType` varchar(16) COLLATE utf8_bin NOT NULL DEFAULT 'NOTSET',
	  `gameName` varchar(16) COLLATE utf8_bin DEFAULT 'NOTSET',
	  `lastPlayedOnInUTC` datetime NOT NULL,
	  `timezoneApplicable` varchar(16) COLLATE utf8_bin DEFAULT 'NOTSET',
	  `endReason` varchar(32) COLLATE utf8_bin DEFAULT NULL,
	  `score` int(11) DEFAULT '-1',
	  `timeTaken` int(11) NOT NULL DEFAULT '-1',
	  PRIMARY KEY (`companyID`,`belongsToDepartment`,`playerID`,`gameID`,`gameSessionUUID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
commmit;

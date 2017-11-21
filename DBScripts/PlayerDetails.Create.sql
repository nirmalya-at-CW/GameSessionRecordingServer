DROP TABLE IF EXISTS `PlayerDetails`;
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


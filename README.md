
# Description
In 1Huddle, we are reimplementing the functionality of recording Game Sessions. At this point in time,
it is PoC and is **very much an WIP**.

# The objective is to run this as an _independent_ Service. It:

 *   Is accessible using REST-style APIs.
 *   Only manages the Game Session, not any of the downstream functionality
 *   Expects the caller to obtain UUID from a different source, but 
     uniqueness of Game Sessions is extremely important for its functioning.
 *   Is self-sufficient for storing and retrieving its own data.
 *   Expects another Service to be available, which consumes the GameSession information
     after it is *Complete* (refer to https://codewalla.atlassian.net/wiki/display/1NFL/Game+Session%2C+its+nature+and+our+approach+-+Part+1)

# Implementation outline

This service (named GameSessionRecordingServer) is implemented using Akka-HTTP and Akka-FSM. The language
used is Scala (which is almost always easily interoperable with Java). The software is bundled and deployed as 
a regular Java JAR file.

For every Game Session, an Actor named *GamePlayRecorderActor (org.nirmalya.experiments.GamePlayRecorderActor)* is created automatically. It represents, in 1Huddle's
backend, the game being played in the real-world. This Actor implements a very small Finite-State-Machine (FSM) and 
responds to a handful events, viz., EvStarted, EvPaused etc. It also sets up a timer for each Game Session, which is
fired to indicate a timeout on the part of the Player. 

Another Actor, named *GameSessionSPOCActor* assumes the role of a gate-keeper. It keeps track of all the *GamePlayRecorderActors*
currently alive and forwards requests (API) made from the external world, to the intended recorders.

A instance of REDIS in-memory database is assumed to be available. Recorder Actors use this for storing all
session-related transient data.

# How to build and run
* Presence of Java (JDK 1.8+) is a pre-requisite.
* Ensure that JAVA_HOME environment variable is set up properly.
* Ensure that $JAVA_HOME/bin and $JAVA_HOME/lib are added to PATH environment variable.
* Ensure that _sbt_ is installed and is also available through the PATH environment variable.
* Be at the directory where this project has been _pulled_ to.
* Fire: _sbt compile test run_
* If it is being run for the first time, sbt will download a number of dependent JARs. Ensure that you are connected to
 'Net and be patient. The whole process may take time.
* If everything goes fine, then the HTTP Server will keep running on _localhost_ , at _port_ 9090.
* At this point, REDIS instance is assumed to be running at the default port (6379). This value is hardcoded.
* At this point, timeout for a game session occurs after 20 seconds. This is hardcoded.

#   Example JSON messages for posting to GameSessionHandlingService

Keys are important, values are just examples.

##  Request to start a game (endpoint:  /start)
    {
        "company":"ABC",
        "manager":"Vikas",
        "playerID":"Nirmalya",
        "gameName":"1Hudd",
        "gameUUID":"A123"
    }
    
##  Response to start a game
    
    {
       "details":"sessionID(ABC.Vikas.playerID.1Hudd.A123), Started."
    }
    
    It is perhaps obvious how the **SessionID** is formed. We are simply 'dot-separating' the fields that are supplied with
    '/start' request. Ensuring uniqueness is the responsibility of the caller. Even if all the other fields are the same,
    'gameUUID' is guaranteed to be unique. 

##  Request to start a game (endpoint:  /play)
    {
       "sessionID":"ABC.Vikas.playerID.1Hudd.A123",
       "questionID":1,
       "answerID":2,
       "isCorrect":true,
       "score":200
    }

##  Response to play a game

    {
       details":"sessionID(ABC.Vikas.playerID.1Hudd.A123), Played Q(1)-A(2)."
    }
    
##  Request to pause a game (endpoint: /pause)
    
    {
           "sessionID":"ABC.Vikas.playerID.1Hudd.A123"
    }
    
##  Response to pause a game

    {
        "details":"sessionID(ABC.Vikas.playerID.1Hudd.A123), Paused."
    }
    
##  Request to end a game (endpoint: /end)
     
    {
        "sessionID":"ABC.Vikas.playerID.1Hudd.A123"
    }
    
## Response to end a game
    
    {
        "details":"sessionID(ABC.Vikas.playerID.1Hudd.A123), Ended."
    }



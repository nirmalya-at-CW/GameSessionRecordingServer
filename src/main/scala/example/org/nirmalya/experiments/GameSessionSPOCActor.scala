package example.org.nirmalya.experiments

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern._
import akka.util.Timeout
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{ExternalAPIParams, GameSessionEndedByPlayer, GameSession, HuddleGame, QuestionAnswerTuple, RecordingStatus}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}


/**
  * Created by nirmalya on 20/6/17.
  */
class GameSessionSPOCActor(gameSessionFinishEmitter: ActorRef) extends Actor with ActorLogging {

  case object ShutYourself


  implicit val executionContext = context.dispatcher
  implicit val askTimeOutDuration:Timeout = Duration(3, "seconds")
  val (redisHost,redisPort) = (
    context.system.settings.config.getConfig("GameSession.redisEndPoint").getString("host"),
    context.system.settings.config.getConfig("GameSession.redisEndPoint").getInt("port")
  )

  val maxGameTimeOut = FiniteDuration(
    context.system.settings.config.getConfig("GameSession.maxGameTimeOut").getInt("duration"),
    TimeUnit.SECONDS)

  var activeGameSessionActors: Map[String, ActorRef] = Map.empty

  def receive = {

    case r: ExternalAPIParams.REQStartAGameWith =>
      val gameSession = GameSession(r.toString, "Ignore")

      if (activeGameSessionActors.isDefinedAt(r.toString))
        sender ! RecordingStatus(s"GameSession with $r is already active.")
      else {
        val originalSender = sender()
        val child = context.actorOf(
          GamePlayRecorderActor(
            true,
            gameSession,
            redisHost,
            redisPort,
            maxGameTimeOut,
            gameSessionFinishEmitter
          ), gameSession.toString)
        context.watch(child)

        this.activeGameSessionActors = this.activeGameSessionActors + Tuple2(r.toString,child)

        val confirmation = (child ? HuddleGame.EvInitiated(System.currentTimeMillis(), gameSession)).mapTo[RecordingStatus]
        confirmation.onComplete {
          case Success(d) =>   originalSender ! d
          case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
        }
      }

    case r: ExternalAPIParams.REQSetQuizForGameWith =>

      val gameSession = GameSession(r.sessionID, "Ignore")

      val originalSender = sender()
      activeGameSessionActors.get(r.sessionID) match {

        case Some (sessionActor) =>

          val confirmation =
            (sessionActor ? HuddleGame.EvQuizIsFinalized(
                                               System.currentTimeMillis(),
                                               r.questions,
                                               gameSession)
            ).mapTo[RecordingStatus]
          confirmation.onComplete {
            case Success(d) =>   originalSender ! d
            case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
          }
        case None                =>
          originalSender ! RecordingStatus(s"No session with ${r.sessionID} exists.")
      }


    case r: ExternalAPIParams.REQPlayAGameWith =>

      val gameSession = GameSession(r.sessionID, "Ignore")

      val originalSender = sender()
      activeGameSessionActors.get(r.sessionID) match {

        case Some (sessionActor) =>

          val confirmation =
            (sessionActor ? HuddleGame.EvQuestionAnswered(
                                          System.currentTimeMillis(),
                                          QuestionAnswerTuple(
                                            r.questionID.toInt,
                                            r.answerID.toInt,
                                            r.isCorrect,
                                            r.score,
                                            r.timeSpentToAnswerAtFE
                                          ),
                                          gameSession
                                       )
            ).mapTo[RecordingStatus]
          confirmation.onComplete {
            case Success(d) =>   originalSender ! d
            case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
          }
        case None                =>
          originalSender ! RecordingStatus(s"No session with ${r.sessionID} exists.")
      }

    case r: ExternalAPIParams.REQPlayAClipWith =>

      val gameSession = GameSession(r.sessionID, "Ignore")

      val originalSender = sender()
      activeGameSessionActors.get(r.sessionID) match {

        case Some (sessionActor) =>

          val confirmation =
            (sessionActor ? HuddleGame.EvPlayingClip(
                                        System.currentTimeMillis(),
                                         r.clipName,
                                         gameSession
                                       )
            ).mapTo[RecordingStatus]
          confirmation.onComplete {
            case Success(d) =>   originalSender ! d
            case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
          }
        case None                =>
          originalSender ! RecordingStatus(s"No session with ${r.sessionID} exists.")
      }

    case r: ExternalAPIParams.REQPauseAGameWith =>
      val gameSession = GameSession(r.sessionID, "Ignore")

      val originalSender = sender()
      activeGameSessionActors.get(r.sessionID) match {

        case Some (sessionActor) =>

          val confirmation = (sessionActor ? HuddleGame.EvPaused(System.currentTimeMillis(), gameSession)).mapTo[RecordingStatus]
          confirmation.onComplete {
            case Success(d) =>   originalSender ! d
            case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
          }
        case None                =>
          originalSender ! RecordingStatus(s"No session with ${r.sessionID} exists.")
      }

    case r: ExternalAPIParams.REQEndAGameWith =>

      val gameSession = GameSession(r.sessionID, "Ignore")

      val originalSender = sender()
      activeGameSessionActors.get(r.sessionID) match {

        case Some (sessionActor) =>

          val confirmation = (sessionActor ? HuddleGame.EvEnded(
                                                System.currentTimeMillis(),
                                                GameSessionEndedByPlayer,
                                                r.totalTimeTakenByPlayer,
                                                gameSession)
            ).mapTo[RecordingStatus]
          confirmation.onComplete {
            case Success(d) =>   originalSender ! d
            case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
          }
        case None                =>
          originalSender ! RecordingStatus(s"No session with ${r.sessionID} exists.")
      }

    // TODO: Revisit the following handler. What is the best way to remember the session that the this
    // TODO: this terminated actor has been seeded with?
    case Terminated(sessionActor) =>

      activeGameSessionActors = activeGameSessionActors - sessionActor.path.name
      log.info(s"Session Actor ($sessionActor) terminated." )


    case (ShutYourself) =>
      context stop(self)

    case (m: Any) =>

      println("Unknown message = [" + m + "] received!")
//      context stop(self)
  }

}

object GameSessionSPOCActor {
  def apply(gameSessionFinishEmitter: ActorRef): Props = Props(new GameSessionSPOCActor(gameSessionFinishEmitter))
}

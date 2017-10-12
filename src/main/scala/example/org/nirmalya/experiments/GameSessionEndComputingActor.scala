package example.org.nirmalya.experiments

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by nirmalya on 12/10/17.
  */
class GameSessionEndComputingActor extends Actor with ActorLogging {


  override def receive = ???

}

object GameSessionEndComputingActor {

  def apply: Props  = Props(new GameSessionEndComputingActor)

}

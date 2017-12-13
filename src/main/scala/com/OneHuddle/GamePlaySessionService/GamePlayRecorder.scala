package com.OneHuddle.GamePlaySessionService

/**
  * Created by nirmalya on 26/8/17.
  */
object GamePlayRecorder extends App {

  // Kamon.start

  GameSessionRecordingServer.start(Array.empty[String])


  /*val someHistogram = Kamon.metrics.histogram("some-histogram")
  val someCounter = Kamon.metrics.counter("some-counter")

  someHistogram.record(42)
  someHistogram.record(50)
  someCounter.increment()

  Thread.sleep(2000)*/

  // This application wont terminate unless you shutdown Kamon.
  // Kamon.shutdown()



}

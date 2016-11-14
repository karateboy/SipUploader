package com.wecc

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.nscala_time.time.Imports._

object ApplicationMain extends App {
  
  val system = ActorSystem("MyActorSystem")
  SipReceiver.startup(system)
  //SipReceiver.parseXML
  
  
//  val uploader = system.actorOf(Uploader.props, "uploader")  
//  if (args.length == 0) {
//    val timer = {
//      import com.github.nscala_time.time.Imports._
//      val nextHour = DateTime.now + 1.hour
//      val uploadTime = nextHour.withMinuteOfHour(9)
//      val duration = new Duration(DateTime.now(), uploadTime)
//
//      system.scheduler.schedule(scala.concurrent.duration.Duration(duration.getStandardSeconds + 1, scala.concurrent.duration.SECONDS),
//        scala.concurrent.duration.Duration(1, scala.concurrent.duration.HOURS), uploader, Uploader.Upload)
//
//    }
//    uploader ! Uploader.Upload
//  }
  //system.awaitTermination()
}
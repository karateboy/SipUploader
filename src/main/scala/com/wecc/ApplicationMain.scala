package com.wecc

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.nscala_time.time.Imports._

object ApplicationMain extends App {
  
  val system = ActorSystem("MyActorSystem")
  SipReceiver.startup(system)
  SipReceiver.parseXML
    
  import scala.concurrent.Await
  import scala.concurrent.duration._
  
  Await.result(system.whenTerminated, Duration.Inf)
}
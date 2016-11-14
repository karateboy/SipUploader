package com.wecc

import akka.actor.{ Actor, ActorLogging, Props }
import javax.xml.ws.Holder
import com.github.nscala_time.time.Imports._

object Uploader {
  val props = Props[Uploader]
  case object Upload
  case class UploadData(time:DateTime)
}

class Uploader extends Actor with ActorLogging {
  import Uploader._
  import com.github.nscala_time.time.Imports._

  def receive = {
    case Upload =>
    case UploadData(time)=>
  }

  
  def upload(hour: DateTime, serviceId: String, user: String, password: String) = {

  }
}
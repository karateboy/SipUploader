package com.wecc

import akka.actor.{ Actor, ActorLogging, Props, ActorRef }
import akka.event.Logging
import akka.actor.ActorSystem
import akka.actor.actorRef2Scala
import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.LoggerFactory
import com.github.nscala_time.time.Imports._


object SipReceiver {
  case object GetInBoxFiles
  case object ParseXML

  import com.typesafe.config.ConfigFactory
  val sipConfig = ConfigFactory.load("sip")
  val sipServer = try{ 
      sipConfig.getString("sipServer")
  }catch{
    case ex:com.typesafe.config.ConfigException=>
      "http://localhost:9000/"
  }
  
  val dataPath = try{ 
    sipConfig.getString("dataPath")
  }catch{
    case ex:com.typesafe.config.ConfigException=>
      "C:/Users/user/Desktop/特殊性工業區/DATBAK"
  }
  
  var receiver: ActorRef = _
  def startup(system: ActorSystem) = {
    Console.println(s"SIP server=${sipServer}")
    Console.println(s"Data path=${dataPath}")
    
    receiver = system.actorOf(Props(classOf[SipReceiver], dataPath), name = "sipReceiver")
  }

  def parseXML = {
    receiver ! ParseXML
  }
}

case class Record(monitor_id: String, monitorType_id: String, time: DateTime, value: Double, status: String)

import javax.inject.Inject
import play.api.libs.ws.WSClient
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

class SipReceiver(path: String) extends Actor with ActorLogging {
  import SipReceiver._
  log.info(s"SipReceiver: path=$path")

  def receive = {
    case ParseXML =>
      try {
        parseAllXml(path)(parser)
        log.info("ParseXML done.")
      } catch {
        case ex: Throwable =>
          log.error("ParseXML failed", ex)
      }
  }

  import java.io.File
  def parser(f: File) {
    import scala.xml.Node
    var dataList = List.empty[Record]
    def processValue(content: String) {
      def recordData() = {
        val origMtId = content.take(4)        
        val mtId = s"${origMtId.take(1)}2${origMtId.substring(2, 4)}"
        val monitorType = MonitorType.map(mtId)
        val monitorStr = content.substring(4, 4 + 4)
        val year = content.substring(8, 8 + 3).toInt
        val dateTimeStr = s"${year + 1911}${content.substring(11, 8 + 13)}"
        val dateTime = DateTime.parse(dateTimeStr, DateTimeFormat.forPattern("YYYYMMddHHmmss"))
        val value =
          try{
            content.substring(21, 21 + 9).toDouble
          }catch{
            case ex:NumberFormatException=>
              0
          }
          
        val status = content.subSequence(30, 30 + 3)
        assert(status.length() == 3)
        val monitorOpt = Monitor.map.get(monitorStr)
        if (monitorOpt.isDefined) {
          val monitor = monitorOpt.get
          dataList = Record(monitor.sip_id, monitorType.desp, dateTime, value, status.toString) :: dataList
        }
      }

      try {
        if (content.length >= 33) {
          val mtId = content.take(4)
          val contentType = mtId(1)
          if (contentType == '4') {
            //calibration...
          } else if (contentType == '9') {
            //log.info("min data")
            recordData
          } else if (contentType == '2') {
            //log.info("hour data")
            recordData
          }
        }
      } catch {
        case ex: Throwable =>
          log.info("Invalid record: {}", ex)
      }
    }

    val node = xml.XML.loadFile(f)
    node match {
      case <emc>{ emcNodes @ _* }</emc> =>
        for (emcNode <- emcNodes) {
          emcNode match {
            case <station_id>{ station_id }</station_id> => //log.info("station_id:" + station_id.text)
            case item @ <item>{ _* }</item> =>
              for (value @ <value>{ _* }</value> <- item.descendant) {
                processValue(value.text)
              }
            case _ =>
          }
        }
    }

    log.info(s"# of record = ${dataList.length}")
    
  }

  def parseAllXml(dir: String)(parser: (File) => Unit) = {

    def listAllFiles = {
      import java.io.FileFilter
      new java.io.File(dir).listFiles()
    }

    def backupFile(fileName: String) = {
      import java.nio.file._
      val srcPath = Paths.get(s"$path$fileName")
      val destPath = Paths.get(s"${path}backup/${fileName}")
      try {
        Files.move(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING)
      } catch {
        case ex: Throwable =>
          log.error(ex, "failed to back...")
      }
    }

    val files = listAllFiles
    for (f <- files) {
      if (f.getName.endsWith("P01_A") || f.getName.endsWith("P01_P")) {
        log.info(s"parse ${f.getName}")
        parser(f)
        //backupFile(f.getName)
      } else {
        f.delete()
      }
    }
  }
}
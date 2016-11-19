package com.wecc

import akka.actor.{ Actor, ActorLogging, Props, ActorRef }
import akka.event.Logging
import akka.actor.ActorSystem
import akka.actor.actorRef2Scala
import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.LoggerFactory
import com.github.nscala_time.time.Imports._

object RecordType extends Enumeration {
  val unknown = Value
  val calibration = Value
  val minData = Value
  val hourData = Value
}

object SipReceiver {
  case object ParseXML

  var receiver: ActorRef = _
  def startup(system: ActorSystem) = {
    receiver = system.actorOf(Props(classOf[SipReceiver]), name = "sipReceiver")
  }

  def parseXML = {
    receiver ! ParseXML
  }

  import java.nio.file.{ Paths, Files, StandardOpenOption }
  import java.nio.charset.{ StandardCharsets }
  import scala.collection.JavaConverters._

  val parsedFileName = "parsed.list"
  var parsedFileList =
    try {
      Files.readAllLines(Paths.get(parsedFileName), StandardCharsets.UTF_8).asScala.toSeq
    } catch {
      case ex: Throwable =>
        Console.println("failed to open parsed.lst")
        Seq.empty[String]
    }

  def appendToParsedFileList(filePath: String) = {
    parsedFileList = parsedFileList ++ Seq(filePath)

    try {
      Files.write(Paths.get(parsedFileName), (filePath + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    } catch {
      case ex: Throwable =>
        Console.println(ex.getMessage)
    }

  }

}

case class SipRecord(monitorId: String, monitorTypeId: String, time: Long, value: Double, status: String)
case class SipCalibration(monitorId: String, monitorTypeId: String, startTime: Long, endTime: Long,
                          span: Double, zero_std: Double, zero_val: Double, span_std: Double, span_val: Double)

import javax.inject.Inject
import play.api.libs.ws.WSClient
import scala.concurrent.Future

class SipReceiver extends Actor with ActorLogging {
  import SipReceiver._
  import com.typesafe.config.ConfigFactory
  val sipConfig = ConfigFactory.load("sip")
  val sipServer = try {
    sipConfig.getString("sipServer")
  } catch {
    case ex: com.typesafe.config.ConfigException =>
      "http://localhost:9000"
  }

  val path = try {
    sipConfig.getString("dataPath")
  } catch {
    case ex: com.typesafe.config.ConfigException =>
      "C:/Users/user/Desktop/特殊性工業區/DATBAK"
  }
  
  val noUpload = try{
    sipConfig.getBoolean("noUpload")
  }catch{
    case ex: com.typesafe.config.ConfigException =>
      true
  }

  log.info(s"Sip Server=$sipServer")
  log.info(s"SipReceiver: path=$path")
  log.info(s"noUpload=$noUpload")
  log.info(s"parsedFileList=${parsedFileList.length}")
  
  def receive = {
    case ParseXML =>
      try {
        parseAllXml(path)(parser)
        log.info("ParseXML done.")
      } catch {
        case ex: Throwable =>
          log.error(ex, "ParseXML failed")
      }
      import scala.concurrent.duration._

      context.system.scheduler.scheduleOnce(scala.concurrent.duration.Duration(1, scala.concurrent.duration.MINUTES), self, ParseXML)
  }

  import java.io.File
  def parser(f: File) = {
    import scala.xml.Node
    var minDataList = List.empty[SipRecord]
    var hourDataList = List.empty[SipRecord]
    var calibrationList = List.empty[SipCalibration]
    def processValue(content: String) {
      def recordData(recordType: RecordType.Value) = {
        val origMtId = content.take(4)
        val mtId = s"${origMtId.take(1)}2${origMtId.substring(2, 4)}"
        val monitorType = MonitorType.map(mtId)
        val monitorStr = content.substring(4, 4 + 4)
        val year = content.substring(8, 8 + 3).toInt
        val dateTimeStr = s"${year + 1911}${content.substring(11, 8 + 13)}"
        val dateTime = DateTime.parse(dateTimeStr, DateTimeFormat.forPattern("YYYYMMddHHmmss"))
        val value =
          try {
            content.substring(21, 21 + 9).toDouble
          } catch {
            case ex: NumberFormatException =>
              0
          }

        val status = content.subSequence(30, 30 + 3)
        assert(status.length() == 3)
        val monitorOpt = Monitor.map.get(monitorStr)
        if (monitorOpt.isDefined) {
          val monitor = monitorOpt.get
          if (recordType == RecordType.minData)
            minDataList = SipRecord(monitor.sip_id, monitorType.desp, dateTime.getMillis, value, status.toString) :: minDataList
          else if (recordType == RecordType.hourData)
            hourDataList = SipRecord(monitor.sip_id, monitorType.desp, dateTime.getMillis, value, status.toString) :: hourDataList
          else
            throw new Exception(s"Unexpected recordType ${recordType}")
        }
      }

      def recordCalibration() = {
        val origMtId = content.take(4)
        val mtId = s"${origMtId.take(1)}2${origMtId.substring(2, 4)}"
        val monitorType = MonitorType.map(mtId)
        val monitorStr = content.substring(4, 4 + 4)
        val startYear = content.substring(8, 8 + 3).toInt
        val startDateTimeStr = s"${startYear + 1911}${content.substring(8 + 3, 8 + 9)}"
        val start = DateTime.parse(startDateTimeStr, DateTimeFormat.forPattern("YYYYMMddHH"))
        val endYear = content.substring(17, 17 + 3).toInt
        val endDateTimeStr = s"${endYear + 1911}${content.substring(17 + 3, 17 + 9)}"
        val end = DateTime.parse(endDateTimeStr, DateTimeFormat.forPattern("YYYYMMddHH"))
        val span = content.substring(26, 26 + 6).toDouble
        val zero_std = content.substring(32, 32 + 9).toDouble
        val zero_val = content.substring(41, 41 + 9).toDouble
        val span_std = content.substring(64, 64 + 9).toDouble
        val span_val = content.substring(73, 73 + 9).toDouble

        val monitorOpt = Monitor.map.get(monitorStr)
        if (monitorOpt.isDefined) {
          val monitor = monitorOpt.get
          calibrationList = SipCalibration(monitor.sip_id, monitorType.desp, start.getMillis, end.getMillis,
            span, zero_std, zero_val, span_std, span_val) :: calibrationList
        }
      }

      try {
        if (content.length >= 33) {
          val mtId = content.take(4)
          val contentType = mtId(1)
          if (contentType == '4') {
            recordCalibration
          } else if (contentType == '9') {
            recordData(RecordType.minData)
          } else if (contentType == '2') {
            recordData(RecordType.hourData)
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

    log.debug(s"# of calibration = ${calibrationList.length}")
    log.debug(s"# of minData = ${minDataList.length}")
    log.debug(s"# of hourData = ${hourDataList.length}")

    if (calibrationList.isEmpty && minDataList.isEmpty && hourDataList.isEmpty)
      log.error(s"Empty upload ${f.getName}")

    def fakeUpload = {
      Future {
        None
      }
    }

    def upload(recordType: RecordType.Value) = {
      import play.api.libs.ws.ahc.AhcWSClient
      import akka.stream.ActorMaterializer
      implicit val materializer = ActorMaterializer()

      val wsClient = AhcWSClient()
      val url = recordType match {
        case RecordType.calibration =>
          "/Calibration"
        case RecordType.minData =>
          "/MinData"
        case RecordType.hourData =>
          "/HourData"
      }

      import play.api.libs.json._
      implicit val dataListWrite = Json.writes[SipRecord]
      implicit val calibrationWrite = Json.writes[SipCalibration]
      val json = recordType match {
        case RecordType.calibration =>
          Json.toJson(calibrationList)
        case RecordType.minData =>
          Json.toJson(minDataList)
        case RecordType.hourData =>
          Json.toJson(hourDataList)
      }

      val request = wsClient.url(s"$sipServer$url").post(json)
      request map {
        ret =>
          val ok = (ret.json \ "Ok").as[Boolean]
          Console.println(s"ret=${ret.status}")

      }
      request.onFailure({
        case ex: Throwable =>
          Console.println(ex, "request failed")
      })

      request.onComplete { x =>
        log.info("wsClient closed.")
        wsClient.close()
      }

      request
    }

    def uploadData = {
      val f1 = if (!calibrationList.isEmpty)
        Some(upload(RecordType.calibration))
      else None

      val f2 = if (!minDataList.isEmpty)
        Some(upload(RecordType.minData))
      else None

      val f3 = if (!hourDataList.isEmpty)
        Some(upload(RecordType.hourData))
      else None

      val retF = List(f1, f2, f3).flatMap { p => p }
      Future.sequence(retF)
    }
    
    if(noUpload)
      fakeUpload
    else
      uploadData
  }

  def parseAllXml[R](dir: String)(parser: (File) => Future[R]) = {

    def listAllFiles = {
      //import java.io.FileFilter
      val allFiles = new java.io.File(dir).listFiles().toList
      
      allFiles.filter( p=> !parsedFileList.contains(p.getName))
    }

    val files = listAllFiles
    for (f <- files) {
      if (f.getName.endsWith("P01_A") || f.getName.endsWith("P01_P")) {
        log.info(s"parse ${f.getName}")
        val retF = parser(f)
        for (ret <- retF) {
          appendToParsedFileList(f.getName)
        }
      } else {
        f.delete()
      }
    }
  }
}
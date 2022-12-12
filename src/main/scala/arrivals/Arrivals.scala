package arrivals

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.alpakka.sse.scaladsl.EventSource
import arrivals.{AkkaStreamUtils, ConfigUtils}
import pureconfig.generic.auto._
import akka.stream.scaladsl._
import akka.stream._
import akka.Done
import akka.NotUsed
import scala.concurrent.duration._
import akka.stream.ThrottleMode
import scala.collection.immutable
import akka.http.scaladsl.model.headers.RawHeader
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import com.typesafe.scalalogging.{LazyLogging}

import java.nio.file.Paths
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import scala.util.Random

import scala.jdk.CollectionConverters._
import spray.json._
import scala.collection.mutable
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.SECONDS
import scala.math._

// App config case class.
case class ArrivalsAppConfig(
  predictionsURI: String,
  APIKey: String,
  eventsFilePath: String
)

object MBTA_Arrivals {

  // Make config implicit.
  implicit val conf = ConfigUtils.loadAppConfig[ArrivalsAppConfig]("arrivals")

  import AkkaStreamUtils.defaultActorSystem._

  // Function required by EventSource to send http requests.
  def rawDataStream(path: String): Source[ByteString, Future[IOResult]] = {

    val file = Paths.get(path)

    val ioRes: Source[ByteString, Future[IOResult]] = FileIO.fromPath(file)

    ioRes
  }

  implicit var pmap = mutable.Map.empty[String, Prediction]

  def updatePrediction(new_prediction: Prediction, orig_prediction: Prediction)(implicit predictionMap: mutable.Map[String, Prediction]): Unit = {
    val vehicle_id = orig_prediction.relationships.vehicle.data.id
    val orig_arrival_time = LocalDateTime.parse(orig_prediction.attributes.arrival_time, DateTimeFormatter.ISO_DATE_TIME)
    val new_arrival_time = LocalDateTime.parse(new_prediction.attributes.arrival_time, DateTimeFormatter.ISO_DATE_TIME)
    val difference = orig_arrival_time.until(new_arrival_time, SECONDS);
    val abs_difference = abs(difference)
    difference match {
      case i: Long if i > 0 => println(s"Train $vehicle_id is running $abs_difference seconds late")
      case j: Long if j < 0 => println(s"Train $vehicle_id is running $abs_difference seconds early")
      case _ => ()
    }
    pmap.update(orig_prediction.id, new_prediction)
  }

  case class Prediction(attributes: Attributes_1, id: String, relationships: Relationships_1)
  case class Attributes_1(arrival_time: String)
  case class Relationships_1(stop: Stop_2, vehicle: Vehicle_2)
  case class Stop_2(data: StopData_3)
  case class StopData_3(id: String)
  case class Vehicle_2(data: VehicleData_3)
  case class VehicleData_3(id: String)

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val vehicleData3Format = jsonFormat1(VehicleData_3)
    implicit val vehicle2Format = jsonFormat1(Vehicle_2)
    implicit val stopData3Format = jsonFormat1(StopData_3)
    implicit val stop2Format = jsonFormat1(Stop_2)
    implicit val relationships1Format = jsonFormat2(Relationships_1)
    implicit val attributes1Format = jsonFormat1(Attributes_1)
    implicit val predictionFormat = jsonFormat3(Prediction)
  }

  def validPrediction(prediction: Prediction): Option[Prediction] = {
    val hasId = prediction.id.size > 0
    val hasArrivalTime = prediction.attributes.arrival_time.size > 0
    val hasStop = prediction.relationships.stop.data.id.size > 0
    val hasVehicle = prediction.relationships.vehicle.data.id.size > 0
    val isValid = hasId && hasArrivalTime && hasStop && hasVehicle
    isValid match {
      case true => Some(prediction)
      case false => None
    }
  }

  def handlePrediction(prediction: Prediction): Unit = {
    val vehicle_id = prediction.relationships.vehicle.data.id
    val stop_id = prediction.relationships.stop.data.id
    val orig_prediction = pmap.get(prediction.id)
    orig_prediction match {
      case None => {
        pmap += (prediction.id -> prediction)
        println(s"Train $vehicle_id will arrive at stop $stop_id at ${prediction.attributes.arrival_time}")
      }
      case Some(op) => updatePrediction(prediction, op)
    }
  }

  def main(args: Array[String]): Unit = {
    import MyJsonProtocol._

    val eSource: Source[ByteString, Future[IOResult]] = 
      rawDataStream(conf.eventsFilePath)

    val eFlow = 
      Flow[ByteString]
        .via(Framing.delimiter(delimiter = ByteString(System.lineSeparator), maximumFrameLength = 10000, allowTruncation = true))
        .map(byteString => byteString.utf8String)
        .throttle(elements = 1, per = 1.second, maximumBurst = 1, mode = ThrottleMode.shaping)

    val eSink = Sink.foreach({
      (bs: String) =>
        val ast = bs.parseJson
        val prediction = ast.convertTo[Prediction]
        val valid_prediction = validPrediction(prediction)
        valid_prediction match {
          case Some(p) => handlePrediction(p)
          case _ => ()
        }
    })

    val f = eSource.via(eFlow).runWith(eSink)

    Await.result(f, Duration.Inf)

  }
}
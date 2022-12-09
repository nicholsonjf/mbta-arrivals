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
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._
import slick.jdbc.GetResult
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

// App config case class.
case class ArrivalsAppConfig(
  predictionsURI: String,
  APIKey: String,
  eventsFilePath: String
)

object MBTA_Arrivals {
  val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("slick-mysql")
  implicit val session = SlickSession.forConfig(databaseConfig)
  import session.profile.api._

  // Make config implicit.
  implicit val conf = ConfigUtils.loadAppConfig[ArrivalsAppConfig]("arrivals")

  import AkkaStreamUtils.defaultActorSystem._
  import AkkaStreamUtils.defaultActorSystem

  // Function required by EventSource to send http requests.
  def rawDataStream(path: String): Source[ByteString, Future[IOResult]] = {

    val file = Paths.get(path)

    val ioRes: Source[ByteString, Future[IOResult]] = FileIO.fromPath(file)

    ioRes
  }

  case class Prediction(attributes: Attributes_1, id: String, relationships: Relationships_1)
  case class Attributes_1(arrival_time: String)
  case class Relationships_1(stop: Stop_2)
  case class Stop_2(data: StopData_3)
  case class StopData_3(id: String)
  case class Vehicle_2(data: VehicleData_3)
  case class VehicleData_3(id: String)

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val vehicleData3Format = jsonFormat1(VehicleData_3)
    implicit val vehicle2Format = jsonFormat1(Vehicle_2)
    implicit val stopData3Format = jsonFormat1(StopData_3)
    implicit val stop2Format = jsonFormat1(Stop_2)
    implicit val relationships1Format = jsonFormat1(Relationships_1)
    implicit val attributes1Format = jsonFormat1(Attributes_1)
    implicit val predictionFormat = jsonFormat3(Prediction)
  }

  case class PredictionRow(id: String, orig_arrival_time: String, updated_arrival_time: String, stop: String)

  class Predictions(tag: Tag) extends Table[PredictionRow](tag, "predictions") {
    def id = column[String]("id", O.PrimaryKey)
    def orig_arrival_time = column[String]("orig_arrival_time")
    def updated_arrival_time = column[String]("updated_arrival_time")
    def stop = column[String]("stop")
    def * = (id, orig_arrival_time, updated_arrival_time, stop).mapTo[PredictionRow]
  }

  val typedPredictions = TableQuery[Predictions]

  def insertPrediction(prediction: PredictionRow): DBIO[Int] = typedPredictions += prediction

  def main(args: Array[String]): Unit = {

    val predictions = TableQuery[Predictions]

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
        val timestamp = prediction.attributes.arrival_time.replace("T", " ")
        val pwts = prediction.copy(attributes = Attributes_1(arrival_time = timestamp))
        val prediction_row = PredictionRow(
          id = pwts.id,
          orig_arrival_time = pwts.attributes.arrival_time,
          updated_arrival_time = "",
          stop = pwts.relationships.stop.data.id
        )
        println(prediction_row)
        prediction_row
    })

    val f = eSource.via(eFlow).runWith(eSink)

    Await.result(f, 10.seconds)
  }
}
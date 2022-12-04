package arrivals

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.alpakka.sse.scaladsl.EventSource
import arrivals.{AkkaStreamUtils, ConfigUtils}
import pureconfig.generic.auto._
import akka.stream.scaladsl._
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

case class ArrivalsAppConfig(
  predictionsURI: String,
  APIKey: String
)

object MBTA_Arrivals {

  implicit val conf = ConfigUtils.loadAppConfig[ArrivalsAppConfig]("arrivals")

  import AkkaStreamUtils.defaultActorSystem._

  def sendHttp(req: HttpRequest): Future[HttpResponse] = {
    val req_with_headers = req.withHeaders(
      List(
        RawHeader("Accept", "text/event-stream"),
        RawHeader("X-API-Key", conf.APIKey)
      )
    )
    val responseFuture: Future[HttpResponse] = Http().singleRequest(req_with_headers)
    responseFuture
      .onComplete {
        case Success(res) => println(res)
        case Failure(_)   => sys.error("something wrong")
      }
    responseFuture
  }

  def main(args: Array[String]): Unit = {

    val eventSource: Source[ServerSentEvent, NotUsed] =
      EventSource(
        uri = Uri(conf.predictionsURI),
        sendHttp,
        initialLastEventId = Some("2"),
        retryDelay = 1.second
      )

    val events: Future[immutable.Seq[ServerSentEvent]] =
    eventSource
      .throttle(elements = 1, per = 500.milliseconds, maximumBurst = 1, ThrottleMode.Shaping)
      .take(3)
      .runWith(Sink.seq)

    val result = Await.ready(events, Duration.Inf).value.get
    result.foreach(println)
  }
}
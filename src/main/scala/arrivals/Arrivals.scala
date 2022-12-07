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

import scala.jdk.CollectionConverters._

// App config case class.
case class ArrivalsAppConfig(
  predictionsURI: String,
  APIKey: String
)

object MBTA_Arrivals {

  // Make config implicit.
  // predictions-uri = "https://stream.wikimedia.org/v2/stream/recentchange"
  implicit val conf = ConfigUtils.loadAppConfig[ArrivalsAppConfig]("arrivals")

  import AkkaStreamUtils.defaultActorSystem._

  // Function required by EventSource to send http requests.
  def sendHttp(req: HttpRequest): Future[HttpResponse] = {
    // Add headers specific to MBTA API.
    // val req_with_headers = req.withHeaders(
    //   List(
    //     RawHeader("Accept", "text/event-stream"),
    //     RawHeader("X-API-Key", conf.APIKey)
    //   )
    // )
    // Send the request.
    Http().singleRequest(req)
  }

  def main(args: Array[String]): Unit = {

    // Open the SSE connection and create a source from received events.
    val eventSource: Source[ServerSentEvent, NotUsed] =
      EventSource(
        uri = Uri(conf.predictionsURI),
        sendHttp,
        initialLastEventId = Some("2"),
        retryDelay = 10.second
      )
    while(true) {

      val events = eventSource.throttle(1, 500.milliseconds, 5, ThrottleMode.Shaping).take(5).runWith(Sink.seq)
      events.onComplete(ev => println(ev))
    }
  }
}
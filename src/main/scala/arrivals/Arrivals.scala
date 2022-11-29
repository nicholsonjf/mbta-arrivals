package arrivals

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.alpakka.sse.scaladsl.EventSource
import scala.concurrent.Future
import arrivals.AkkaStreamUtils
import akka.stream.scaladsl._
import akka.Done
import akka.NotUsed
import scala.concurrent.duration._
import akka.stream.ThrottleMode
import scala.collection.immutable
import akka.http.scaladsl.model.headers.RawHeader

object MBTA_Arrivals {

  import AkkaStreamUtils.defaultActorSystem._

  def sendHttp(req: HttpRequest): Future[HttpResponse] = {
    val req_with_headers = req.withHeaders(
      List(
        RawHeader("Accept", "text/event-stream"),
        RawHeader("X-API-Key", "API_KEY")
      )
    )
    Http().singleRequest(req_with_headers)
  }

  def main(args: Array[String]): Unit = {

    val eventSource: Source[ServerSentEvent, NotUsed] =
      EventSource(
        uri = Uri("https://api-v3.mbta.com/predictions/?filter\\[route\\]=Red&include=schedule,stop"),
        sendHttp,
        initialLastEventId = Some("2"),
        retryDelay = 1.second
      )

    val events: Future[immutable.Seq[ServerSentEvent]] =
    eventSource
      .throttle(elements = 1, per = 500.milliseconds, maximumBurst = 1, ThrottleMode.Shaping)
      .take(50)
      .runWith(Sink.seq)
  }
}
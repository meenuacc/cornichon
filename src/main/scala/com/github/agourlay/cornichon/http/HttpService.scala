package com.github.agourlay.cornichon.http

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpHeader, HttpResponse, StatusCode }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.Materializer
import cats.data.Xor
import cats.data.Xor.{ left, right }
import spray.json.{ JsValue, JsObject }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

class HttpService(implicit actorSystem: ActorSystem, materializer: Materializer) {

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  private def requestRunner(req: HttpRequest) = {
    Http()
      .singleRequest(req)
      .flatMap(expectJson)
      .recover(exceptionMapper)
  }

  def postJson(payload: JsValue, url: String, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] =
    requestRunner(Post(url, payload).withHeaders(headers))

  def putJson(payload: JsValue, url: String, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] =
    requestRunner(Put(url, payload).withHeaders(headers))

  def getJson(url: String, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] =
    requestRunner(Get(url).withHeaders(headers))

  def deleteJson(url: String, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] =
    requestRunner(Delete(url).withHeaders(headers))

  def exceptionMapper: PartialFunction[Throwable, Xor[HttpError, JsonHttpResponse]] = {
    case e: TimeoutException ⇒ left(TimeoutError(e.getMessage))
  }

  def expectJson(httpResponse: HttpResponse): Future[Xor[HttpError, JsonHttpResponse]] =
    Unmarshal(httpResponse).to[JsValue].map { body: JsValue ⇒
      right(JsonHttpResponse.fromResponse(httpResponse, body))
    }.recover {
      case e: Exception ⇒ left(JsonError(e))
    }
}

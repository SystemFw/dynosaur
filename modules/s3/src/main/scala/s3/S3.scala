/*
 * Copyright 2018 OVO Energy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ovoenergy.comms.aws
package s3

import headers._
import model._
import common._
import common.model._
import cats.implicits._
import cats.effect.{Sync, Resource, ConcurrentEffect, ExitCase}
import cats.effect.implicits._
import java.nio.ByteBuffer

import auth.AwsSigner
import org.http4s.syntax.all._
import org.http4s.{Service => _, _}
import org.http4s.headers._
import scalaxml._
import Method._
import client.Client
import client.blaze.BlazeClientBuilder
import client.dsl.Http4sClientDsl
import org.http4s.Header.Raw

import scala.concurrent.ExecutionContext
import scala.xml.Elem

trait S3[F[_]] {

  def headObject(bucket: Bucket, key: Key): F[Either[Error, ObjectSummary]]

  def getObject(bucket: Bucket, key: Key): F[Either[Error, Object[F]]]

  def getObjectAs[A](bucket: Bucket, key: Key)(
      f: (ObjectSummary, fs2.Stream[F, Byte]) => F[A]): F[Either[Error, A]]

  def putObject(
      bucket: Bucket,
      key: Key,
      content: ObjectContent[F],
      metadata: Map[String, String] = Map.empty): F[Either[Error, ObjectPut]]

}

object S3 {

  def resource[F[_]: ConcurrentEffect](
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None,
      ec: ExecutionContext = ExecutionContext.global): Resource[F, S3[F]] = {
    BlazeClientBuilder[F](ec).resource.map(client =>
      S3.apply(client, credentialsProvider, region, endpoint))
  }

  def apply[F[_]: Sync](
      client: Client[F],
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None): S3[F] = new S3[F] with Http4sClientDsl[F] {

    private val signer = AwsSigner[F](credentialsProvider, region, Service.S3)
    private val signedClient = signer(client)

    private val baseEndpoint = endpoint.getOrElse {
      if (region == Region.`us-east-1`) {
        Uri.uri("https://s3.amazonaws.com")
      } else {
        // TODO use the total version, we may need a S3 builder that returns F[S3[F]]
        Uri.unsafeFromString(s"https://s3-${region.value}.amazonaws.com")
      }
    }

    private def uri(bucket: Bucket, key: Key) = {
      // TODO It only supports path style access ATM
      val bucketEndpoint = baseEndpoint / bucket.name

      key.value.split("/", -1).foldLeft(bucketEndpoint) { (acc, x) =>
        acc / x
      }
    }

    private implicit val errorEntityDecoder: EntityDecoder[F, Error] =
      EntityDecoder[F, Elem].flatMapR { elem =>
        val code = Option(elem \ "Code")
          .filter(_.length == 1)
          .map(_.text)
          .map(Error.Code.apply)

        val message = Option(elem \ "Message")
          .filter(_.length == 1)
          .map(_.text)

        val requestId = Option(elem \ "RequestId")
          .filter(_.length == 1)
          .map(_.text)
          .map(RequestId.apply)

        (code, message, requestId)
          .mapN { (code, message, requestId) =>
            val bucketName = elem.child
              .find(_.label == "BucketName")
              .map(node => Bucket(node.text))
            val key =
              elem.child.find(_.label == "Key").map(node => Key(node.text))

            Error(
              code = code,
              requestId = requestId,
              message = message,
              key = key,
              bucketName = bucketName
            )
          }
          .fold[DecodeResult[F, Error]](
            DecodeResult.failure(InvalidMessageBodyFailure(
              "Code, RequestId and Message XML elements are mandatory"))
          )(error => DecodeResult.success(error))
      }

    private implicit val objectPutDecoder: EntityDecoder[F, ObjectPut] =
      new EntityDecoder[F, ObjectPut] {

        override def decode(
            msg: Message[F],
            strict: Boolean): DecodeResult[F, ObjectPut] = {
          msg.headers
            .get(ETag)
            .map(_.tag.tag)
            .map(Etag.apply)
            .map(etag => ObjectPut(etag))
            // TODO InvalidMessageBodyFailure is not correct here as there si no body
            .fold[DecodeResult[F, ObjectPut]](
              DecodeResult.failure(
                InvalidMessageBodyFailure("The ETag header must be present"))
            )(ok => DecodeResult.success(ok))
        }

        override val consumes: Set[MediaRange] =
          MediaRange.standard.values.toSet
      }

    /**
      * Return an S3 [[Object]] in the given bucket and key. If the object does not exist, it will return an [[Error]].
      *
      * BEWARE: that once the returned effect is resolved and the result is a [[Object]] you will to consume the body.
      */
    def getObject(bucket: Bucket, key: Key): F[Either[Error, Object[F]]] = {

      def parseDisposableResponse(
          rr: Resource[F, Response[F]]): F[Either[Error, Object[F]]] = {
        rr.allocated.bracketCase {
          case (response, release) =>
            if (response.status.isSuccess) {
              parseObjectSummary(response)
                .leftWiden[Throwable]
                .value
                .rethrow
                .map { os =>
                  Object(
                    os,
                    response.body.onFinalize(release)
                  ).asRight[Error]
                }
            } else {
              (response.as[Error].map(_.asLeft[Object[F]])).guarantee(release)
            }
        } {
          case ((_, release), ExitCase.Canceled) => release
          case _ => ().pure[F]
        }
      }

      for {
        request <- GET(uri(bucket, key))
        result <- parseDisposableResponse(signedClient.run(request))
      } yield result
    }

    def getObjectAs[A](bucket: Bucket, key: Key)(
        f: (ObjectSummary, fs2.Stream[F, Byte]) => F[A])
      : F[Either[Error, A]] = {

      def handleOk(response: Response[F]): F[A] = {
        parseObjectSummary(response)
          .leftWiden[Throwable]
          .value
          .rethrow
          .flatMap { summary =>
            f(summary, response.body)
          }
      }

      for {
        request <- GET(uri(bucket, key))
        result <- signedClient.fetch(request) { response =>
          if (response.status.isSuccess) {
            handleOk(response).map(_.asRight[Error])
          } else {
            response.as[Error].map(_.asLeft[A])
          }
        }
      } yield result
    }

    def headObject(
        bucket: Bucket,
        key: Key): F[Either[Error, ObjectSummary]] = {

      for {
        request <- GET(uri(bucket, key))
        result <- signedClient.fetch(request) {
          case r if r.status.isSuccess =>
            r.as[ObjectSummary].map(_.asRight[Error])
          case r =>
            r.as[Error].map(_.asLeft[ObjectSummary])
        }
      } yield result
    }

    def putObject(
        bucket: Bucket,
        key: Key,
        content: ObjectContent[F],
        metadata: Map[String, String] = Map.empty)
      : F[Either[Error, ObjectPut]] = {

      def initHeaders: F[Headers] =
        Sync[F]
          .fromEither(`Content-Length`.fromLong(content.contentLength))
          .map { contentLength =>
            Headers
              .of(
                contentLength,
                `Content-Type`(content.mediaType, content.charset),
              )
              .put(metadata.map {
                case (k, v) => Raw(s"${`X-Amz-Meta-`}$k".ci, v)
              }.toSeq: _*)
          }

      val extractContent: F[Array[Byte]] =
        (content.contentLength > ObjectContent.MaxDataLength)
          .pure[F]
          .ifM(
            Sync[F].raiseError(new IllegalArgumentException(
              s"The content is too long to be transmitted in a single chunk, max allowed content length: ${ObjectContent.MaxDataLength}")),
            content.data.chunks.compile
              .fold(ByteBuffer.allocate(content.contentLength.toInt))(
                (buffer, chunk) => buffer put chunk.toByteBuffer)
              .map(_.array())
          )

      for {
        hs <- initHeaders
        contentAsSingleChunk <- extractContent
        request <- PUT(contentAsSingleChunk, uri(bucket, key), hs.toList: _*)
        result <- signedClient.fetch(request) { r =>
          if (r.status.isSuccess) r.as[ObjectPut].map(_.asRight[Error])
          else r.as[Error].map(_.asLeft[ObjectPut])
        }
      } yield result
    }

    private implicit val objectSummaryDecoder: EntityDecoder[F, ObjectSummary] =
      EntityDecoder.decodeBy(MediaRange.`*/*`)(parseObjectSummary)

    private def parseObjectSummary(
        response: Message[F]): DecodeResult[F, ObjectSummary] = {
      val etag: DecodeResult[F, Etag] = response.headers
        .get(ETag)
        .map(_.tag.tag)
        .map(Etag.apply)
        .map(DecodeResult.success[F, Etag](_))
        .getOrElse(DecodeResult.failure[F, Etag](MalformedMessageBodyFailure(
          "ETag header must be present on the response")))

      val metadata: Map[String, String] = response.headers.toList.collect {
        case h if h.name.value.toLowerCase.startsWith(`X-Amz-Meta-`) =>
          h.name.value.substring(`X-Amz-Meta-`.length) -> h.value
      }.toMap

      etag.map { eTag =>
        model.ObjectSummary(eTag, metadata)
      }
    }

  }

}

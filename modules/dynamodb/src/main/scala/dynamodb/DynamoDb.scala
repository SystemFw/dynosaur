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
package dynamodb

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.implicits._

import io.circe.syntax._

import org.http4s.Uri
import org.http4s.EntityDecoder
import org.http4s.Method._
import org.http4s.headers._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.{ResponseLogger, RequestLogger}

import auth.AwsSigner
import common._
import common.model._
import common.headers._
import common.mediaTypes._
import model._
import codec._

trait DynamoDb[F[_]] {

  def putItem(
      tableName: TableName,
      item: AttributeValue.M,
      returnValues: ReturnValues = ReturnValues.None): F[PutItemResponse] =
    putItem(PutItemRequest(tableName, item, returnValues))

  def putItem(request: PutItemRequest): F[PutItemResponse]

}

object DynamoDb {

  def resource[F[_]: ConcurrentEffect](
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None,
      ec: ExecutionContext = ExecutionContext.global)
    : Resource[F, DynamoDb[F]] = {
    BlazeClientBuilder[F](ec).resource.map(client =>
      DynamoDb(client, credentialsProvider, region, endpoint))
  }

  def apply[F[_]: Concurrent](
      client: Client[F],
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None) = {

    val signer = AwsSigner[F](credentialsProvider, region, Service.DynamoDb)
    val requestLogger: Client[F] => Client[F] =
      RequestLogger[F](logHeaders = true, logBody = true)
    val responseLogger: Client[F] => Client[F] =
      ResponseLogger[F](logHeaders = true, logBody = true)

    val signedClient = signer(requestLogger(responseLogger(client)))

    val baseEndpoint: F[Uri] = endpoint
      .map(_.pure[F])
      .getOrElse {
        Uri
          .fromString(s"https://dynamodb.${region.value}.amazonaws.com")
          .leftWiden[Throwable]
          .raiseOrPure[F]
      }

    implicit val decodePutItemResponseAsJson
      : EntityDecoder[F, PutItemResponse] =
      jsonOf[F, PutItemResponse]

    implicit val decodeDynamoDbErrorAsJson: EntityDecoder[F, DynamoDbError] =
      jsonOf[F, DynamoDbError]

    new DynamoDb[F] with Http4sClientDsl[F] {
      override def putItem(request: PutItemRequest): F[PutItemResponse] = {
        for {
          endpoint <- baseEndpoint
          request <- POST(
            request.asJson,
            endpoint / "",
            `X-Amz-Target`("DynamoDB_20120810.PutItem"),
            `Content-Type`(`application/x-amz-json-1.0`))
          result <- signedClient.expectOr[PutItemResponse](request) {
            response =>
              response.as[DynamoDbError].widen[Throwable]
          }
        } yield result
      }
    }
  }

}

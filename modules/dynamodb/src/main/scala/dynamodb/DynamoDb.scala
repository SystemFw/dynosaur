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

import cats.effect.Sync
import cats.implicits._

import org.http4s.Uri
import org.http4s.Method._
import org.http4s.headers._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

import auth.AwsSigner
import common._
import common.model._
import common.headers._
import common.mediaTypes._
import model._

trait DynamoDb[F[_]] {

  def putItem(
      tableName: TableName,
      item: AttributeValue.M,
      returnValues: ReturnValues): F[PutItemResponse] =
    putItem(PutItemRequest(tableName, item, returnValues))

  def putItem(request: PutItemRequest): F[PutItemResponse]

}

object DynamoDb {

  def apply[F[_]: Sync](
      client: Client[F],
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None) = {

    val signer = AwsSigner[F](credentialsProvider, region, Service.S3)
    val signedClient = signer(client)

    val baseEndpoint: F[Uri] = endpoint
      .map(_.pure[F])
      .getOrElse {
        Uri
          .fromString(s"dynamodb.${region.value}.amazonaws.com")
          .leftWiden[Throwable]
          .raiseOrPure[F]
      }

    new DynamoDb[F] with Http4sClientDsl[F] {
      override def putItem(request: PutItemRequest): F[PutItemResponse] = {

        ???
        // for {
        //   endpoint <- baseEndpoint
        //   request <- PUT("", endpoint, `X-Amz-Target`("DynamoDB_20120810.PutItem"), `Content-Type`(`application/x-amz-json-1.0`))
        //   result <- signedClient.fetch(request) { r =>
        //     if (r.status.isSuccess) r.as[PutItemResponse].map(_.asRight[Error])
        //     else r.as[DynamoDbError].map(_.asLeft[PutItemResponse])
        //   }
        // } yield result
      }
    }
  }

}

/*

POST / HTTP/1.1
Host: dynamodb.<region>.<domain>;
Accept-Encoding: identity
Content-Length: <PayloadSizeBytes>
User-Agent: <UserAgentString>
Content-Type: application/x-amz-json-1.0
Authorization: AWS4-HMAC-SHA256 Credential=<Credential>, SignedHeaders=<Headers>, Signature=<Signature>
X-Amz-Date: <Date>
X-Amz-Target: DynamoDB_20120810.PutItem

 */

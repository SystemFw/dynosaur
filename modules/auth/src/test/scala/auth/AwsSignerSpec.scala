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
package auth

import common._
import headers._
import model._
import Credentials._

import java.security.MessageDigest
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit

import cats.implicits._
import cats.effect.IO
import fs2._
import fs2.hash._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.headers._
import org.http4s.{HttpDate, MediaType, Request, Uri}
import AwsSigner._
import org.http4s.syntax.all._

class AwsSignerSpec extends UnitSpec with Http4sClientDsl[IO] {

  "digest" should {
    "calculate the correct digest" in forAll() { data: Array[Byte] =>
      val d = MessageDigest.getInstance("SHA-256").digest(data)
      val testValue = Stream(data: _*).through(sha256).toList
      testValue shouldBe d.toList
    }

    "calculate the correct digest for empty body" in {
      val md = MessageDigest.getInstance("SHA-256")
      val expectedResult = encodeHex(md.digest(Array.empty))

      note(s"Expected empty body hash: $expectedResult")

      Stream.empty
        .covaryOutput[Byte]
        .through(sha256)
        .fold(Vector.empty[Byte])(_ :+ _)
        .map(xs => encodeHex(xs.toArray))
        .toList
        .head shouldBe expectedResult

    }
  }

  "Request with no body" should {
    "not have empty body stream" in {
      (for {
        req <- GET.apply(Uri.uri("https://example.com"))
        last <- req.body.compile.last
      } yield last).futureValue shouldBe None
    }
  }

  "uriEncoder" should {
    "encode :" in {
      uriEncode("foo:bar") shouldBe "foo%3Abar"
    }

    "encode %2F" in {
      uriEncode("%2F") shouldBe "%252F"
    }
  }

  "AwsSigner.fixRequest" when {

    "Date header is not defined" when {
      "X-Amz-Date is not defined" should {
        "Add X-Amz-Date" in {

          val now = Instant.now()
          val expectedXAmzDate = `X-Amz-Date`(HttpDate.unsafeFromInstant(now))

          withFixedRequest(
            GET(Uri.uri("http://example.com"))
              .map(_.removeHeader(Date).removeHeader(`X-Amz-Date`)),
            now) { r =>
            IO {
              r.headers.get(Date) shouldBe None
              r.headers.get(`X-Amz-Date`) shouldBe Some(expectedXAmzDate)
            }
          }.futureValue
        }
      }

      "X-Amz-Date is defined" should {
        "not add X-Amz-Date" in {
          val now = Instant.now()
          val expectedXAmzDate = `X-Amz-Date`(
            HttpDate.unsafeFromInstant(now.minus(5, ChronoUnit.MINUTES)))
          withFixedRequest(
            GET(Uri.uri("http://example.com"))
              .map(_.removeHeader(Date).putHeaders(expectedXAmzDate)),
            now) { r =>
            IO {
              r.headers.get(Date) shouldBe None
              r.headers.get(`X-Amz-Date`) shouldBe Some(expectedXAmzDate)
            }
          }.futureValue
        }
      }
    }

    "Date header is defined" should {
      "not add X-Amz-Date" in {
        val now = Instant.now()
        val expectedDate =
          Date(HttpDate.unsafeFromInstant(now.minus(5, ChronoUnit.MINUTES)))
        withFixedRequest(
          GET(Uri.uri("http://example.com"))
            .map(_.removeHeader(`X-Amz-Date`).putHeaders(expectedDate)),
          now) { r =>
          IO {
            r.headers.get(`X-Amz-Date`) shouldBe None
            r.headers.get(Date) shouldBe Some(expectedDate)
          }
        }.unsafeRunSync()
      }

    }

    "Host header is defined" when {
      "the uri is absolute" should {
        "not add Host header" in {
          val expectedHost = Host("foo", 5555)
          withFixedRequest(GET(Uri.uri("http://example.com"))
            .map(_.putHeaders(expectedHost))) { r =>
            IO {
              r.headers.get(Host) shouldBe Some(expectedHost)
            }
          }.unsafeRunSync()
        }
      }

      "the uri is relative" should {
        "fail the effect" in {
          withFixedRequest(GET(Uri.uri("/foo/bar")))(_ => IO.unit).attempt
            .unsafeRunSync() shouldBe a[Left[_, _]]
        }
      }

    }

    "Credentials contain the session token" should {
      "add the X-Amz-Security-Token header" in {

        val sessionToken = SessionToken("this-is-a-token")
        val credentials = Credentials(
          AccessKeyId("FOO"),
          SecretAccessKey("BAR"),
          sessionToken.some)
        val expectedXAmzSecurityToken = `X-Amz-Security-Token`(sessionToken)
        withFixedRequest(
          GET(Uri.uri("http://example.com")),
          credentials = credentials) { r =>
          IO {
            r.headers.get(`X-Amz-Security-Token`) shouldBe Some(
              expectedXAmzSecurityToken)
          }
        }.futureValue
      }
    }

    "Credentials do not contain the session token" should {
      "not add the X-Amz-Security-Token header" in {

        val credentials =
          Credentials(AccessKeyId("FOO"), SecretAccessKey("BAR"))
        withFixedRequest(
          GET(Uri.uri("http://example.com")),
          credentials = credentials) { r =>
          IO {
            r.headers.get(`X-Amz-Security-Token`) shouldBe None
          }
        }.futureValue
      }
    }
  }

  "AwsSigner.signRequest" when {
    "Date header is not defined" when {
      "X-Amz-Date is not defined" should {
        "return a failed effect" in {
          withSignRequest(GET(Uri.uri("http://example.com")))(_ => IO.unit).attempt.futureValue shouldBe a[
            Left[_, _]]
        }
      }
    }
  }

  "AwsSigner.signRequest" should {
    "sign a vannilla GET request correctly" in {

      val expectedAuthorizationValue =
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31"

      val credentials = Credentials(
        AccessKeyId("AKIDEXAMPLE"),
        SecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"))

      val dateTime = LocalDateTime
        .parse("20150830T123600Z", AwsSigner.dateTimeFormatter)
        .atZone(ZoneOffset.UTC)

      val request = GET(
        Uri.uri("/"),
        Host("example.amazonaws.com"),
        `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime)))

      withSignRequest(
        request,
        credentials = credentials,
        region = Region.`us-east-1`,
        service = Service("service")) { r =>
        IO(
          r.headers
            .get("Authorization".ci)
            .get
            .value shouldBe expectedAuthorizationValue)
      }.futureValue
    }

    "sign a vanilla POST request correctly" in {

      val expectedAuthorizationValue =
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=5da7c1a2acd57cee7505fc6676e4e544621c30862966e37dddb68e92efbe5d6b"
      val credentials = Credentials(
        AccessKeyId("AKIDEXAMPLE"),
        SecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"))
      val dateTime = LocalDateTime
        .parse("20150830T123600Z", AwsSigner.dateTimeFormatter)
        .atZone(ZoneOffset.UTC)

      val request = POST(
        Uri.uri("/"),
        Host("example.amazonaws.com"),
        `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime)))

      withSignRequest(
        request,
        credentials = credentials,
        region = Region.`us-east-1`,
        service = Service("service")) { r =>
        IO(
          r.headers
            .get("Authorization".ci)
            .get
            .value)
      }.futureValue shouldBe expectedAuthorizationValue
    }

    "sign a POST request with body" in {

      val expectedAuthorizationValue =
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=content-type;host;x-amz-date, Signature=ff11897932ad3f4e8b18135d722051e5ac45fc38421b1da7b9d196a0fe09473a"
      val credentials = Credentials(
        AccessKeyId("AKIDEXAMPLE"),
        SecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"))
      val dateTime = LocalDateTime
        .parse("20150830T123600Z", AwsSigner.dateTimeFormatter)
        .atZone(ZoneOffset.UTC)

      val request = POST
        .apply(
          "Param1=value1",
          Uri.uri("/"),
          Host("example.amazonaws.com"),
          `Content-Type`(MediaType.application.`x-www-form-urlencoded`),
          `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime))
        )
        .map(_.removeHeader(`Content-Length`))

      withSignRequest(
        request,
        credentials = credentials,
        region = Region.`us-east-1`,
        service = Service("service")) { r =>
        IO(
          r.headers
            .get("Authorization".ci)
            .get
            .value shouldBe expectedAuthorizationValue)
      }.futureValue
    }
  }

  def withFixedRequest[A](
      req: IO[Request[IO]],
      now: Instant = Instant.now(),
      credentials: Credentials =
        Credentials(AccessKeyId("FOO"), SecretAccessKey("BAR")))(
      f: Request[IO] => IO[A]): IO[A] = {
    for {
      request <- req
      fixedRequest <- AwsSigner.fixRequest(request, credentials, now)
      result <- f(fixedRequest)
    } yield result
  }

  def withSignRequest[A](
      req: IO[Request[IO]],
      region: Region = Region.`eu-west-1`,
      service: Service = Service.DynamoDb,
      credentials: Credentials =
        Credentials(AccessKeyId("FOO"), SecretAccessKey("BAR")))(
      f: Request[IO] => IO[A]): IO[A] = {
    for {
      request <- req
      signedRequest <- AwsSigner.signRequest(
        request,
        credentials,
        region,
        service)
      result <- f(signedRequest)
    } yield result
  }
}

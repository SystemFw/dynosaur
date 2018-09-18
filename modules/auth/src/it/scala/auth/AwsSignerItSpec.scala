package com.ovoenergy.comms.aws
package auth

import common._
import common.model._
import cats.effect.{IO, ContextShift}
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import cats.implicits._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.headers._
import org.http4s.{Uri, Status, MediaType}
import org.http4s.client.middleware.{ResponseLogger, RequestLogger}

import scala.concurrent.duration._

class AwsSignerItSpec extends IntegrationSpec with Http4sClientDsl[IO] {

  implicit val ctx: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  // This is our UAT environment
  private val esEndpoint = ""

  "AwsSigner" should {
    "sign request valid for S3" in {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service.S3)

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.unsafeFromString("https://s3-eu-west-1.amazonaws.com/ovo-comms-test/more.pdf"))
          status <- signedClient.status(req)
        } yield {
          status.isSuccess shouldBe true
        }
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds))
    }

    "sign request valid for S3 with nested paths" in {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service.S3)

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.unsafeFromString("https://s3-eu-west-1.amazonaws.com/ovo-comms-test/test/more.pdf"))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES GET" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.unsafeFromString(s"${esEndpoint}/audit-2018-09/_doc/foo"))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES POST" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString(s"${esEndpoint}/audit-2018-09/_doc/_search"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES POST with multiple indexes" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString(s"${esEndpoint}/audit-2018-09,audit-2018-10,audit-2018-11/_doc/_search?ignore_unavailable=true"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES POST with query" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString(s"${esEndpoint}/audit-2018-09/_doc/_search?ignore_unavailable=true"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES POST with query and multiple parameters" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString(s"${esEndpoint}/audit-2018-09/_doc/_search?ignore_unavailable=true&refresh=true"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }

    "sign request valid for ES POST with query and multiple parameters with comas and stars" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString("/audit-2018-09/_doc/_search?ignore_unavailable=true&refresh=true&foo*=foo&bar,baz=baz"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }


    "sign request valid for ES POST with star in path" ignore {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service("es"))

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        val body = """
        {
          "query" : {
            "term": {
              "_id": "6fc53f9d-99a0-4938-a486-31fb401c5bc4"
            }
          }
        }
        """

        for {
          req <- POST(body, Uri.unsafeFromString(s"${esEndpoint}/audit-*/_doc/_search"), `Content-Type`(MediaType.application.json))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
    }
  }

  def withHttpClient[A](f: Client[IO] => IO[A]): IO[A] = {
    Http1Client
      .stream[IO]()
      .evalMap(f)
      .compile
      .last
      .map(
        _.toRight[Throwable](new IllegalStateException("The stream was empty")))
      .rethrow
  }

}

package com.ovoenergy.comms.aws
package dynamodb

import java.time._

import scala.concurrent.duration._

import cats.implicits._
import cats.effect.{IO, ContextShift}

import model._
import common.{IntegrationSpec, CredentialsProvider}
import common.model._

class DynamoDbSpec extends IntegrationSpec {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), 500.millis)
  implicit val ctx: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global) 


  "DynamoDb" should {
    "write an item" in {
      withDynamoDb { dynamoDb =>
        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          response <- dynamoDb.putItem(
            TableName("comms-aws-test"), 
            AttributeValue.M(Map(
              AttributeName("id")->AttributeValue.S("test-1"),
              AttributeName("date")->AttributeValue.N(now.toString)   
            ))
          )
        } yield response
      }.futureValue shouldBe PutItemResponse(None)
    } 

    "write an item and return overwritten one" in {
      withDynamoDb { dynamoDb =>
        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          oldItem = AttributeValue.M(Map(
            AttributeName("id")->AttributeValue.S("test-1"),
            AttributeName("date")->AttributeValue.N(now.toString),
            AttributeName("value")->AttributeValue.S("I am the old one")
          ))
          newItem = AttributeValue.M(Map(
            AttributeName("id")->AttributeValue.S("test-1"),
            AttributeName("date")->AttributeValue.N(now.toString),
            AttributeName("value")->AttributeValue.S("I am the new one")
          ))
          _ <- dynamoDb.putItem(
            TableName("comms-aws-test"), 
            oldItem
          )
          response <- dynamoDb.putItem(
            TableName("comms-aws-test"), 
            newItem,
            ReturnValues.AllOld
          )
        } yield response.attributes shouldBe Some(oldItem)
      }.futureValue
    } 
  }

  def withDynamoDb[A](f: DynamoDb[IO] => IO[A]): IO[A] = {
    DynamoDb.resource(CredentialsProvider.default[IO], Region.`eu-west-1`).use(f)
  }

}
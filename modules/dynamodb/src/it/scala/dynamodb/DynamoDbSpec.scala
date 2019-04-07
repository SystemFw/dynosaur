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
          response <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = AttributeValue.M(Map(
              AttributeName("id")->AttributeValue.S("test-1"),
              AttributeName("date")->AttributeValue.N(now.toString)   
            ))
          ))
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
          _ <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = oldItem
          ))
          response <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = newItem,
            returnValues = ReturnValues.AllOld
          ))
        } yield response.attributes shouldBe Some(oldItem)
      }.futureValue
    }
    
    "get an item" in {
      withDynamoDb { dynamoDb =>

        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          item = AttributeValue.M(Map(
            AttributeName("id")->AttributeValue.S("test-1"),
            AttributeName("date")->AttributeValue.N(now.toString)   
          ))
          _ <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = item
          ))
          response <- dynamoDb.getItem(GetItemRequest(
            tableName = TableName("comms-aws-test"), 
            key = AttributeValue.M(Map(
              AttributeName("id")->AttributeValue.S("test-1"),
              AttributeName("date")->AttributeValue.N(now.toString)   
            )),
            consistent = true
          ))
        } yield response shouldBe GetItemResponse(Some(item))
      }.futureValue
    } 

    "delete an item" in {
      withDynamoDb { dynamoDb =>

        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          item = AttributeValue.M(Map(
            AttributeName("id")->AttributeValue.S("test-1"),
            AttributeName("date")->AttributeValue.N(now.toString)   
          ))
          _ <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = item
          ))
          _ <- dynamoDb.deleteItem(DeleteItemRequest(
            tableName = TableName("comms-aws-test"), 
            key = item
          ))
          response <- dynamoDb.getItem(GetItemRequest(
            tableName = TableName("comms-aws-test"), 
            key = AttributeValue.M(Map(
              AttributeName("id")->AttributeValue.S("test-1"),
              AttributeName("date")->AttributeValue.N(now.toString)   
            )),
            consistent = true
          ))
        } yield response shouldBe GetItemResponse(None)
      }.futureValue
    } 

    "update an item" in {
      withDynamoDb { dynamoDb =>

        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          key = AttributeValue.M(Map(
            AttributeName("id")->AttributeValue.S("test-1"),
            AttributeName("date")->AttributeValue.N(now.toString)   
          ))
          item = AttributeValue.M(key.values ++ Map(
            AttributeName("value")->AttributeValue.S("1")  
          ))
          _ <- dynamoDb.putItem(PutItemRequest(
            tableName = TableName("comms-aws-test"), 
            item = item
          ))
          _ <- dynamoDb.updateItem(UpdateItemRequest(
            tableName = TableName("comms-aws-test"), 
            key = key,
            updateExpression = UpdateExpression("SET #v = :newValue"),
            expressionAttributeNames = Map(
              "#v" -> AttributeName("value")
            ),
            expressionAttributeValues = Map(
              ":newValue" -> AttributeValue.S("2")
            )
          ))
          response <- dynamoDb.getItem(GetItemRequest(
            tableName = TableName("comms-aws-test"), 
            key = key,
            consistent = true
          ))
        } yield response shouldBe GetItemResponse(Some(AttributeValue.M(key.values ++ Map(
          AttributeName("value")->AttributeValue.S("2")  
        ))))
      }.futureValue
    } 
  }

  def withDynamoDb[A](f: DynamoDb[IO] => IO[A]): IO[A] = {
    DynamoDb.resource(CredentialsProvider.default[IO], Region.`eu-west-1`).use(f)
  }

}
package dynosaur

import java.time._

import scala.concurrent.duration._

import cats.implicits._
import cats.effect.{IO, ContextShift}


import com.ovoenergy.comms.aws.common.{IntegrationSpec, CredentialsProvider}
import com.ovoenergy.comms.aws.common.model._

import model._

class DynamoDbSpec extends IntegrationSpec {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(30.seconds), 500.millis)
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
              ExpressionAlias("#v") -> AttributeName("value")
            ),
            expressionAttributeValues = Map(
              ExpressionPlaceholder(":newValue") -> AttributeValue.S("2")
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

    "write multiple items" in {
      withDynamoDb { dynamoDb =>
        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          response <- dynamoDb.batchWriteItems(
            BatchWriteItemsRequest(
              requestItems = Map(
                TableName("comms-aws-test") -> List(
                  BatchWriteItemsRequest.PutRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-1"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                  BatchWriteItemsRequest.PutRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-2"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                )
              )
            )
          )
        } yield response
      }.futureValue shouldBe BatchWriteItemsResponse(Map.empty)
    } 

    "write and delete multiple items" in {
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
          response <- dynamoDb.batchWriteItems(
            BatchWriteItemsRequest(
              requestItems = Map(
                TableName("comms-aws-test") -> List(
                  BatchWriteItemsRequest.DeleteRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-1"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                  BatchWriteItemsRequest.PutRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-2"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                )
              )
            )
          )
        } yield response
      }.futureValue shouldBe BatchWriteItemsResponse(Map.empty)
    } 

    "delete multiple items" in {
      withDynamoDb { dynamoDb =>
        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          response <- dynamoDb.batchWriteItems(
            BatchWriteItemsRequest(
              requestItems = Map(
                TableName("comms-aws-test") -> List(
                  BatchWriteItemsRequest.DeleteRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-1"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                  BatchWriteItemsRequest.DeleteRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S("test-2"),
                        AttributeName("date")->AttributeValue.N(now.toString)   
                      )
                    )
                  ),
                )
              )
            )
          )
        } yield response
      }.futureValue shouldBe BatchWriteItemsResponse(Map.empty)
    } 

    "return unprocessed items" in {
      withDynamoDb { dynamoDb =>

        // 25 items is the max batch size
        val ids = (0 to 24).map(idx => s"test-$idx")
        
        // 380K string
        val longString = (0 to 380 * 1024).map(_ => 65 + (math.random * 25).toInt).map(_.toChar).mkString
        for {
          now <- IO(Instant.now).map(_.toEpochMilli)
          response <- dynamoDb.batchWriteItems(
            BatchWriteItemsRequest(
              requestItems = Map(
                TableName("comms-aws-test") -> ids.map(id => 
                  BatchWriteItemsRequest.PutRequest(
                    AttributeValue.M(
                      Map(
                        AttributeName("id")->AttributeValue.S(id),
                        AttributeName("date")->AttributeValue.N(now.toString),
                        AttributeName("data")->AttributeValue.s(longString)   
                      )
                    )
                  )
                ).toList
              )
            )
          )
        } yield response
      }.futureValue.unprocessedItems(TableName("comms-aws-test")) should not be empty
    } 
  }

  def withDynamoDb[A](f: DynamoDb[IO] => IO[A]): IO[A] = {
    DynamoDb.resource(CredentialsProvider.default[IO], Region.`eu-west-1`).use(f)
  }

}

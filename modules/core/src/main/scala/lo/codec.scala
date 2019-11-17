/*
 * Copyright 2019 OVO Energy
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

package dynosaur
package lo

import cats.Id
import cats.implicits._
import cats.data.NonEmptyList

import io.circe._
import io.circe.syntax._
import io.circe.literal._
import io.circe.{Decoder, Encoder}

import scodec.bits.ByteVector

import dynosaur.model.{AttributeName, AttributeValue, NonEmptySet}
import model._

object codec {

  implicit val attributeNameKeyEncoder: KeyEncoder[AttributeName] =
    new KeyEncoder[AttributeName] {
      override def apply(an: AttributeName): String = an.value
    }

  implicit val attributeNameKeyDecoder: KeyDecoder[AttributeName] =
    new KeyDecoder[AttributeName] {
      override def apply(key: String): Option[AttributeName] =
        Some(AttributeName(key))
    }

  implicit val expressionAliasKeyEncoder: KeyEncoder[ExpressionAlias] =
    new KeyEncoder[ExpressionAlias] {
      override def apply(an: ExpressionAlias): String = an.value
    }

  implicit val expressionAliasKeyDecoder: KeyDecoder[ExpressionAlias] =
    new KeyDecoder[ExpressionAlias] {
      override def apply(key: String): Option[ExpressionAlias] =
        ExpressionAlias.fromString(key).toOption
    }

  implicit val expressionPlaceholderKeyEncoder
      : KeyEncoder[ExpressionPlaceholder] =
    new KeyEncoder[ExpressionPlaceholder] {
      override def apply(an: ExpressionPlaceholder): String = an.value
    }

  implicit val expressionPlaceholderKeyDecoder
      : KeyDecoder[ExpressionPlaceholder] =
    new KeyDecoder[ExpressionPlaceholder] {
      override def apply(key: String): Option[ExpressionPlaceholder] =
        ExpressionPlaceholder.fromString(key).toOption
    }

  implicit val tableNameKeyEncoder: KeyEncoder[TableName] =
    KeyEncoder.encodeKeyString.contramap(_.value)

  implicit val tableNameKeyDecoder: KeyDecoder[TableName] =
    KeyDecoder.decodeKeyString.map(TableName.apply)

  implicit val attributeNameEncoder: Encoder[AttributeName] =
    Encoder.encodeString.contramap(_.value)

  implicit val attributeNameDecoder: Decoder[AttributeName] =
    Decoder.decodeString.map(AttributeName.apply)

  implicit val expressionPlaceholderEncoder: Encoder[ExpressionPlaceholder] =
    Encoder.encodeString.contramap(_.value)

  implicit val expressionPlaceholderDecoder: Decoder[ExpressionPlaceholder] =
    Decoder.decodeString.emap(ExpressionPlaceholder.fromString)

  implicit val expressionAliasEncoder: Encoder[ExpressionAlias] =
    Encoder.encodeString.contramap(_.value)

  implicit val expressionAliasDecoder: Decoder[ExpressionAlias] =
    Decoder.decodeString.emap(ExpressionAlias.fromString)

  implicit val conditionExpressionEncoder: Encoder[ConditionExpression] =
    Encoder.encodeString.contramap(_.value)

  implicit val projectionExpressionEncoder: Encoder[ProjectionExpression] =
    Encoder.encodeString.contramap(_.value)

  implicit val updateExpressionEncoder: Encoder[UpdateExpression] =
    Encoder.encodeString.contramap(_.value)

  implicit def nonEmptySetEncoder[A: Encoder]: Encoder[NonEmptySet[A]] =
    Encoder[NonEmptyList[A]].contramap { nes: NonEmptySet[A] =>
      NonEmptyList.fromListUnsafe(nes.toSet.toList)
    }

  implicit def nonEmptySetDecoder[A: Decoder]: Decoder[NonEmptySet[A]] =
    Decoder[NonEmptyList[A]].map { nel: NonEmptyList[A] =>
      NonEmptySet.unsafeFromSet(nel.toList.toSet)
    }

  implicit lazy val encodeAttributeValueM: Encoder[AttributeValue.M] =
    encodeAttributeValue.contramap(identity)

  implicit lazy val encodeAttributeValue: Encoder[AttributeValue] =
    Encoder.instance {
      case AttributeValue.NULL =>
        json"""{"NULL": true}"""
      case AttributeValue.BOOL(value) =>
        json"""{"BOOL": ${value}}"""
      case AttributeValue.B(value) =>
        json"""{"B": ${value.toBase64}}"""
      case AttributeValue.BS(values) =>
        val encodedValues: NonEmptySet[String] =
          values.unsafeWithSet(_.map(_.toBase64).pure[Id])
        json"""{"BS": ${encodedValues}}"""
      case AttributeValue.N(value) =>
        json"""{"N": ${value}}"""
      case AttributeValue.NS(values) =>
        json"""{"NS": ${values}}"""
      case AttributeValue.S(value) =>
        json"""{"S": ${value}}"""
      case AttributeValue.SS(values) =>
        json"""{"SS": ${values}}"""
      case AttributeValue.L(values) =>
        json"""{"L": ${values}}"""
      case AttributeValue.M(values) =>
        json"""{"M": ${values}}"""
    }

  implicit lazy val decodeAttributeValueM: Decoder[AttributeValue.M] =
    Decoder.instance { hc =>
      for {
        xs <- hc.get[Map[AttributeName, AttributeValue]]("M")
      } yield AttributeValue.M(xs)
    }

  implicit lazy val decodeAttributeValue: Decoder[AttributeValue] = {

    val decodeNULL: Decoder[AttributeValue] =
      Decoder[Boolean]
        .as(AttributeValue.NULL)
        .prepare(_.downField("NULL"))
        .widen[AttributeValue]

    val decodeBOOL: Decoder[AttributeValue] =
      Decoder[Boolean]
        .map(AttributeValue.BOOL(_))
        .prepare(_.downField("BOOL"))
        .widen[AttributeValue]

    val decodeN: Decoder[AttributeValue] =
      Decoder[String]
        .map(AttributeValue.N(_))
        .prepare(_.downField("N"))
        .widen[AttributeValue]

    val decodeNS: Decoder[AttributeValue] =
      Decoder[NonEmptySet[String]]
        .map(AttributeValue.NS(_))
        .prepare(_.downField("NS"))
        .widen[AttributeValue]

    val decodeS: Decoder[AttributeValue] =
      Decoder[String]
        .map(AttributeValue.S(_))
        .prepare(_.downField("S"))
        .widen[AttributeValue]

    val decodeSS: Decoder[AttributeValue] =
      Decoder[NonEmptySet[String]]
        .map(AttributeValue.SS(_))
        .prepare(_.downField("SS"))
        .widen[AttributeValue]

    val decodeB: Decoder[AttributeValue] =
      Decoder[String]
        .emap(
          str =>
            ByteVector
              .fromBase64(str)
              .toRight(s"$str is not a valid base64")
        )
        .map(AttributeValue.B(_))
        .prepare(_.downField("B"))
        .widen[AttributeValue]

    val decodeBS: Decoder[AttributeValue] = {
      import alleycats.std.set._

      Decoder[NonEmptySet[String]]
        .emap {
          _.unsafeWithSet {
            _.traverse { x =>
              ByteVector.fromBase64(x).toRight(s"$x is not a valid base64")
            }
          }
        }
        .map(AttributeValue.BS(_))
        .prepare(_.downField("BS"))
        .widen[AttributeValue]
    }

    val decodeL: Decoder[AttributeValue] = Decoder.instance { hc =>
      for {
        xs <- hc.get[Vector[AttributeValue]]("L")
      } yield AttributeValue.L(xs)
    }

    val decodeM: Decoder[AttributeValue] =
      decodeAttributeValueM.widen[AttributeValue]

    decodeNULL <+> decodeBOOL <+> decodeS <+> decodeSS <+> decodeN <+> decodeNS <+> decodeB <+> decodeBS <+> decodeL <+> decodeM
  }

  implicit lazy val encodeReturnValues: Encoder[ReturnValues] =
    Encoder[String].contramap {
      case ReturnValues.None => "NONE"
      case ReturnValues.AllOld => "ALL_OLD"
      case ReturnValues.AllNew => "ALL_NEW"
      case ReturnValues.UpdatedOld => "UPDATED_OLD"
      case ReturnValues.UpdatedNew => "UPDATED_NEW"
    }

  implicit lazy val encodeTableName: Encoder[TableName] =
    Encoder[String].contramap(_.value)

  implicit lazy val encodePutItemRequest: Encoder[PutItemRequest] =
    Encoder.instance { request =>
      val jsMap: Map[String, Json] = Map(
        "Item" -> extractM(request.item.asJson),
        "ReturnValues" -> request.returnValues.asJson,
        "TableName" -> request.tableName.asJson
      ) ++ request.conditionExpression.map(
        x => "ConditionExpression" -> x.value.asJson
      ) ++
        request.expressionAttributeNames.map(
          x => "ExpressionAttributeNames" -> x.asJson
        ) ++
        request.expressionAttributeValues.map(
          x => "ExpressionAttributeValues" -> x.asJson
        )

      Json.obj(jsMap.toSeq: _*)
    }

  implicit lazy val decodePutItemResponse: Decoder[PutItemResponse] =
    Decoder.instance { hc =>
      for {
        attributes <- hc
          .get[Option[Map[AttributeName, AttributeValue]]]("Attributes")
      } yield PutItemResponse(attributes.map(AttributeValue.M))
    }

  implicit lazy val encodeGetItemRequest: Encoder[GetItemRequest] =
    Encoder.instance { request =>
      val jsMap: Map[String, Json] = Map(
        "TableName" -> request.tableName.asJson,
        "Key" -> extractM(request.key.asJson),
        "ConsistentRead" -> request.consistent.asJson
      ) ++
        request.projectionExpression.map(
          x => "ProjectionExpression" -> x.asJson
        ) ++
        request.expressionAttributeNames.map(
          x => "ExpressionAttributeNames" -> x.asJson
        )

      Json.obj(jsMap.toSeq: _*)

    }

  implicit lazy val decodeGetItemResponse: Decoder[GetItemResponse] =
    Decoder.instance { hc =>
      for {
        item <- hc.get[Option[Map[AttributeName, AttributeValue]]]("Item")
      } yield GetItemResponse(item.map(AttributeValue.M))
    }

  implicit lazy val encodeDeleteItemRequest: Encoder[DeleteItemRequest] =
    Encoder.instance { request =>
      val jsMap: Map[String, Json] = Map(
        "TableName" -> request.tableName.asJson,
        "Key" -> extractM(request.key.asJson),
        "ReturnValues" -> request.returnValues.asJson
      ) ++
        request.conditionExpression.map(
          x => "ConditionExpression" -> x.value.asJson
        ) ++
        request.expressionAttributeNames.map(
          x => "ExpressionAttributeNames" -> x.asJson
        ) ++
        request.expressionAttributeValues.map(
          x => "ExpressionAttributeValues" -> x.asJson
        )

      Json.obj(jsMap.toSeq: _*)
    }

  implicit lazy val decodeDeleteItemResponse: Decoder[DeleteItemResponse] =
    Decoder.instance { hc =>
      for {
        attributes <- hc
          .get[Option[AttributeValue.M]]("Attributes")
      } yield DeleteItemResponse(attributes)
    }

  implicit lazy val encodeUpdateItemRequest: Encoder[UpdateItemRequest] =
    Encoder.instance { request =>
      val jsMap: Map[String, Json] = Map(
        "TableName" -> request.tableName.asJson,
        "Key" -> extractM(request.key.asJson),
        "UpdateExpression" -> request.updateExpression.asJson,
        "ReturnValues" -> request.returnValues.asJson
      ) ++
        request.conditionExpression.map(
          x => "ConditionExpression" -> x.value.asJson
        ) ++
        (if (request.expressionAttributeNames.isEmpty) {
           Map.empty
         } else {
           Map(
             "ExpressionAttributeNames" -> request.expressionAttributeNames.asJson
           )
         }) ++
        (if (request.expressionAttributeValues.isEmpty) {
           Map.empty
         } else {
           Map(
             "ExpressionAttributeValues" -> request.expressionAttributeValues.asJson
           )
         })

      Json.obj(jsMap.toSeq: _*)
    }

  implicit lazy val decodeUpdateItemResponse: Decoder[UpdateItemResponse] =
    Decoder.instance { hc =>
      for {
        attributes <- hc
          .get[Option[AttributeValue.M]]("Attributes")
      } yield UpdateItemResponse(attributes)
    }

  implicit lazy val encodeBatchWriteItemsRequest
      : Encoder[BatchWriteItemsRequest] = Encoder.instance { request =>
    implicit val encodeWriteRequest
        : Encoder[BatchWriteItemsRequest.WriteRequest] = Encoder.instance {
      case BatchWriteItemsRequest.PutRequest(item) =>
        Json.obj("PutRequest" -> Json.obj("Item" -> extractM(item.asJson)))
      case BatchWriteItemsRequest.DeleteRequest(key) =>
        Json.obj("DeleteRequest" -> Json.obj("Key" -> extractM(key.asJson)))
    }

    Json.obj("RequestItems" -> request.requestItems.asJson)
  }

  implicit lazy val decodeBatchWriteItemsRequest
      : Decoder[BatchWriteItemsResponse] = Decoder.instance { hc =>
    implicit val decodeWriteRequest
        : Decoder[BatchWriteItemsRequest.WriteRequest] = {
      val decodePut = Decoder.instance(
        _.downField("PutRequest")
          .downField("Item")
          .as[Map[AttributeName, AttributeValue]]
          .map(xs => AttributeValue.M(xs))
          .map(
            item =>
              BatchWriteItemsRequest
                .PutRequest(item): BatchWriteItemsRequest.WriteRequest
          )
      )
      val decodeDelete = Decoder.instance(
        _.downField("DeleteRequest")
          .downField("Key")
          .as[Map[AttributeName, AttributeValue]]
          .map(xs => AttributeValue.M(xs))
          .map(
            key =>
              BatchWriteItemsRequest
                .DeleteRequest(key): BatchWriteItemsRequest.WriteRequest
          )
      )
      decodePut <+> decodeDelete
    }

    for {
      unprocessedItems <- hc
        .downField("UnprocessedItems")
        .as[Map[TableName, List[BatchWriteItemsRequest.WriteRequest]]]
    } yield BatchWriteItemsResponse(unprocessedItems)
  }

  implicit lazy val decodeDynamoDbError: Decoder[DynamoDbError] =
    Decoder.instance { hc =>
      for {
        message <- hc.get[String]("Message").orElse(hc.get[String]("message"))
      } yield DynamoDbError(message)
    }

  def extractM(jso: Json): Json =
    jso.withObject(jso => jso("M").getOrElse(Json.Null))

}

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
package model

import dynosaur.model.{AttributeRef, AttributeValue}

import cats.implicits._
import scala.reflect.macros.whitebox

case class TableName(value: String)

sealed trait ReturnValues
object ReturnValues {

  /**
    * nothing is returned
    */
  case object None extends ReturnValues

  /**
    * the content of the old item is returned
    */
  case object AllOld extends ReturnValues

  case object UpdatedOld extends ReturnValues
  case object AllNew extends ReturnValues
  case object UpdatedNew extends ReturnValues

}

case class ConditionExpression(value: String)

case class ProjectionExpression(value: String)

case class UpdateExpression(value: String)

// TODO Macro to instantiate it from static string
case class ExpressionAlias private (value: String)

object ExpressionAlias {

  class Macros(val c: whitebox.Context) {
    import c.universe._

    def literal(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          ExpressionAlias
            .fromString(s)
            .fold(
              e => c.abort(c.enclosingPosition, e),
              _ =>
                q"_root_.dynosaur.lo.model.ExpressionAlias.unsafeFromString($s)"
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid ExpressionAlias. Use ExpressionAlias.fromString if you have a dynamic String that you want to parse."
          )
      }
  }

  def apply(s: String): ExpressionAlias = macro ExpressionAlias.Macros.literal

  def fromString(str: String): Either[String, ExpressionAlias] =
    if (str.headOption.contains('#')) {
      new ExpressionAlias(str).asRight
    } else {
      s"Valid expression alias must start with '#'. Invalid placeholder: '$str'".asLeft
    }

  def unsafeFromString(str: String): ExpressionAlias =
    fromString(str).getOrElse(
      throw new IllegalArgumentException(
        s"$str is not a valid expression alias"
      )
    )
}

// TODO Macro to instantiate it from static string
case class ExpressionPlaceholder private (value: String)

object ExpressionPlaceholder {

  class Macros(val c: whitebox.Context) {
    import c.universe._

    def literal(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          ExpressionPlaceholder
            .fromString(s)
            .fold(
              e => c.abort(c.enclosingPosition, e),
              _ =>
                q"_root_.dynosaur.lo.model.ExpressionPlaceholder.unsafeFromString($s)"
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid ExpressionPlaceholder. Use ExpressionPlaceholder.fromString if you have a dynamic String that you want to parse."
          )
      }
  }

  def apply(s: String): ExpressionPlaceholder =
    macro ExpressionPlaceholder.Macros.literal

  def fromString(str: String): Either[String, ExpressionPlaceholder] =
    if (str.headOption.contains(':'))
      new ExpressionPlaceholder(str).asRight
    else
      s"Valid expression placeholdert must start with ':'. Invalid placeholder: '$str'".asLeft

  def unsafeFromString(str: String): ExpressionPlaceholder =
    fromString(str).fold(e => throw new IllegalArgumentException(e), identity)

}

case class PutItemRequest(
    tableName: TableName,
    item: AttributeValue.M,
    conditionExpression: Option[ConditionExpression] = None,
    expressionAttributeNames: Map[ExpressionAlias, AttributeRef] = Map.empty,
    expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
      Map.empty,
    returnValues: ReturnValues = ReturnValues.None
)

case class PutItemResponse(attributes: Option[AttributeValue.M])

case class GetItemRequest(
    tableName: TableName,
    key: AttributeValue.M,
    consistent: Boolean = false,
    projectionExpression: Option[ProjectionExpression] = None,
    expressionAttributeNames: Map[ExpressionAlias, AttributeRef] = Map.empty
)

case class GetItemResponse(
    item: Option[AttributeValue.M]
)

case class DeleteItemRequest(
    tableName: TableName,
    key: AttributeValue.M,
    conditionExpression: Option[ConditionExpression] = None,
    expressionAttributeNames: Map[ExpressionAlias, AttributeRef] = Map.empty,
    expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
      Map.empty,
    returnValues: ReturnValues = ReturnValues.None
)

case class DeleteItemResponse(
    attributes: Option[AttributeValue.M]
)

case class UpdateItemRequest(
    tableName: TableName,
    key: AttributeValue.M,
    updateExpression: UpdateExpression,
    expressionAttributeNames: Map[ExpressionAlias, AttributeRef] = Map.empty,
    expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
      Map.empty,
    conditionExpression: Option[ConditionExpression] = None,
    returnValues: ReturnValues = ReturnValues.None
)

case class UpdateItemResponse(
    attributes: Option[AttributeValue.M]
)

object BatchWriteItemsRequest {
  sealed trait WriteRequest
  case class DeleteRequest(key: AttributeValue.M) extends WriteRequest
  case class PutRequest(item: AttributeValue.M) extends WriteRequest
}

case class BatchWriteItemsRequest(
    requestItems: Map[TableName, List[BatchWriteItemsRequest.WriteRequest]]
)

case class BatchWriteItemsResponse(
    unprocessedItems: Map[TableName, List[
      BatchWriteItemsRequest.WriteRequest
    ]]
)

// TODO Model all the DynamoDb errors
case class DynamoDbError(message: String, retriable: Boolean = false)
    extends Exception(message)

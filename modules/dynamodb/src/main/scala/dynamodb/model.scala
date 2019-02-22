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

import org.http4s.Status
import cats.data.Chain
import scodec.bits._

object model {

  case class TableName(value: String)

  case class AttributeName(value: String)

  sealed trait AttributeValue
  object AttributeValue {
    case object NULL extends AttributeValue
    case class S(value: String) extends AttributeValue
    case class N(value: String) extends AttributeValue
    case class B(value: ByteVector) extends AttributeValue
    case class BOOL(value: Boolean) extends AttributeValue
    case class M(values: Map[AttributeName, AttributeValue])
        extends AttributeValue
    case class L(values: List[AttributeValue]) extends AttributeValue
    case class SS(values: Set[String]) extends AttributeValue
    case class NS(values: Set[String]) extends AttributeValue
    case class BS(values: Set[ByteVector]) extends AttributeValue
  }

  /*

ReturnValues
Use ReturnValues if you want to get the item attributes as they appeared before they were updated with the PutItem request. For PutItem, the valid values are:

NONE - If ReturnValues is not specified, or if its value is NONE, then nothing is returned. (This setting is the default for ReturnValues.)

ALL_OLD - If PutItem overwrote an attribute name-value pair, then the content of the old item is returned.

Note

The ReturnValues parameter is used by several DynamoDB operations; however, PutItem does not recognize any values other than NONE or ALL_OLD.

Type: String

Valid Values: NONE | ALL_OLD | UPDATED_OLD | ALL_NEW | UPDATED_NEW

Required: No

   */

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

  case class PutItemRequest(
      tableName: TableName,
      item: AttributeValue.M,
      returnValues: ReturnValues,
  )

  case class PutItemResponse(attributes: Option[AttributeValue.M])

  // TODO Model all the DynamoDb errors
  case class DynamoDbError(
      status: Status,
      message: String,
      retriable: Boolean = false)
      extends Exception(message)

  /*

    TODO Model Capacity

    case class CapacityUnits(value: Double)
    case class Capacity(total: CapacityUnits, read: CapacityUnits, write: CapacityUnits)

     "ConsumedCapacity": {
      "CapacityUnits": number,
      "GlobalSecondaryIndexes": {
         "string" : {
            "CapacityUnits": number,
            "ReadCapacityUnits": number,
            "WriteCapacityUnits": number
         }
      },
      "LocalSecondaryIndexes": {
         "string" : {
            "CapacityUnits": number,
            "ReadCapacityUnits": number,
            "WriteCapacityUnits": number
         }
      },
      "ReadCapacityUnits": number,
      "Table": {
         "CapacityUnits": number,
         "ReadCapacityUnits": number,
         "WriteCapacityUnits": number
      },
      "TableName": "string",
      "WriteCapacityUnits": number
   },

 */

}

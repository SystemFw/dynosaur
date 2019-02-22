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
  case class DynamoDbError(message: String, retriable: Boolean = false)
      extends Exception(message)

}

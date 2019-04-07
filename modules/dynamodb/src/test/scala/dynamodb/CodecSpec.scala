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

import io.circe.syntax._
import io.circe.literal._

import common._
import model._
import codec._

import Arbitraries._

class CodecSpec extends UnitSpec {

  "arbitraries" should {
    "generate any AttributeValue" in forAll { attributeValue: AttributeValue =>
      note(s"AttributeValue: $attributeValue")
    }
  }

  "codec" should {
    "encode any AttributeValue" in forAll { attributeValue: AttributeValue =>
      val json = attributeValue.asJson
      note(s"Json: ${json.noSpaces}")
    }

    "encode/decode any AttributeValue" in forAll {
      attributeValue: AttributeValue =>
        attributeValue.asJson.as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.NULL" in {
      (AttributeValue.NULL: AttributeValue).asJson
        .as[AttributeValue] shouldBe Right(AttributeValue.NULL)
    }

    "encode/decode AttributeValue.BOOL" in forAll {
      attributeValue: AttributeValue.BOOL =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.S" in forAll {
      attributeValue: AttributeValue.S =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.SS" in forAll {
      attributeValue: AttributeValue.SS =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.N" in forAll {
      attributeValue: AttributeValue.N =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.NS" in forAll {
      attributeValue: AttributeValue.NS =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.B" in forAll {
      attributeValue: AttributeValue.B =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.BS" in forAll {
      attributeValue: AttributeValue.BS =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "encode/decode AttributeValue.L" in forAll {
      attributeValue: AttributeValue.L =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe a[Right[_, _]]
    }

    "encode/decode AttributeValue.M" in forAll {
      attributeValue: AttributeValue.M =>
        (attributeValue: AttributeValue).asJson
          .as[AttributeValue] shouldBe Right(attributeValue)
    }

    "decode any PutItemResponse" in forAll { response: PutItemResponse =>
      val json = json"""{"Attributes": ${response.attributes.map(_.values)}}"""

      json.as[PutItemResponse] shouldBe Right(response)
    }

    "encode any PutItemRequest" in forAll { request: PutItemRequest =>
      request.asJson
    }

    "decode any GetItemResponse" in forAll { response: GetItemResponse =>
      val json = json"""{"Item": ${response.item.map(_.values)}}"""

      json.as[GetItemResponse] shouldBe Right(response)
    }

    "encode any GetItemRequest" in forAll { request: GetItemRequest =>
      request.asJson
    }
  }
}

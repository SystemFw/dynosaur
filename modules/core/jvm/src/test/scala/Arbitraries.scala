/*
 * Copyright 2020 Fabio Labella
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

import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.core.SdkBytes

import CollectionConverters.all._

object Arbitraries {

  implicit def arbitraryForGen[A](implicit
      genForA: Gen[A]
  ): Arbitrary[A] =
    Arbitrary(
      genForA
    )

  // Be careful with the deep, it will increase the test time a lot
  implicit lazy val genForAttributeValue: Gen[AttributeValue] =
    genAttributeValue(2)

  implicit lazy val genForDynamoValue: Gen[DynamoValue] =
    genForAttributeValue.map(DynamoValue.apply)

  def genAttributeValue(deep: Int) = if (deep > 0) {
    Gen.oneOf(genNestedAttributeValue(deep), genLeafAttributeValue)
  } else {
    genLeafAttributeValue
  }

  def genLeafAttributeValue: Gen[AttributeValue] = Gen.oneOf(
    Gen.const(AttributeValue.builder.nul(true).build),
    Gen
      .choose[Int](Int.MinValue, Int.MaxValue)
      .map(n => AttributeValue.builder.n(n.toString).build),
    Gen
      .choose[Long](Long.MinValue, Long.MaxValue)
      .map(n => AttributeValue.builder.n(n.toString).build),
    Gen
      .choose[Float](Float.MinValue, Float.MaxValue)
      .map(n => AttributeValue.builder.n(n.toString).build),
    Gen
      .choose[Double](Double.MinValue, Double.MaxValue)
      .map(n => AttributeValue.builder.n(n.toString).build),
    arbitrary[Set[Int]]
      .map(_.map(_.toString))
      .map(ns => AttributeValue.builder.ns(ns.toList: _*).build),
    arbitrary[String].map(s => AttributeValue.builder.s(s).build),
    arbitrary[Set[String]].map(ss =>
      AttributeValue.builder.ss(ss.toList: _*).build
    ),
    arbitrary[Boolean].map(s => AttributeValue.builder.bool(s).build),
    arbitrary[Array[Byte]]
      .map(SdkBytes.fromByteArray)
      .map(b => AttributeValue.builder.b(b).build),
    arbitrary[Array[Array[Byte]]]
      .map(bss => bss.map(SdkBytes.fromByteArray))
      .map(bs => AttributeValue.builder.bs(bs: _*).build)
  )

  def genNestedAttributeValue(deep: Int): Gen[AttributeValue] = Gen.oneOf(
    Gen
      .listOf(Gen.lzy(genAttributeValue(deep - 1)))
      .map(l => AttributeValue.builder.l(l: _*).build),
    Gen
      .mapOf(
        Gen.zip(arbitrary[String], Gen.lzy(genAttributeValue(deep - 1)))
      )
      .map(m => AttributeValue.builder.m(m.asJava).build)
  )
}

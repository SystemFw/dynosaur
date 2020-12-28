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

import cats._, syntax.all._
import scodec.bits.ByteVector
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.core.SdkBytes

import dynosaur.CollectionConverters.all._

case class DynamoValue(value: AttributeValue) {

  val s: Option[String] =
    Option(value.s)

  val n: Option[DynamoValue.Number] =
    Option(value.n).map(DynamoValue.Number(_))

  val bool: Option[Boolean] =
    Option(value.bool).map(_.booleanValue)

  val l: Option[List[DynamoValue]] =
    value.hasL
      .guard[Option]
      .as(value.l.asScala.toList.map(DynamoValue(_)))

  val m: Option[Map[String, DynamoValue]] =
    value.hasM
      .guard[Option]
      .as(value.m.asScala.toMap.map { case (k, v) => k -> DynamoValue(v) })

  val nul: Option[Unit] =
    Option(value.nul).void

  val b: Option[ByteVector] =
    Option(value.b).map(b => ByteVector(b.asByteArray))

  val bs: Option[NonEmptySet[ByteVector]] =
    value.hasBs
      .guard[Option] >> NonEmptySet.fromSet(
      value.bs.asScala.toSet.map((b: SdkBytes) => ByteVector(b.asByteArray))
    )

  val ns: Option[NonEmptySet[DynamoValue.Number]] =
    value.hasNs
      .guard[Option] >> NonEmptySet.fromSet(
      value.ns.asScala.toSet.map(DynamoValue.Number(_))
    )

  val ss: Option[NonEmptySet[String]] =
    value.hasSs.guard[Option] >> NonEmptySet.fromSet(value.ss.asScala.toSet)
}
object DynamoValue {
  def make(
      build: AttributeValue.Builder => AttributeValue.Builder
  ): DynamoValue =
    DynamoValue(build(AttributeValue.builder).build)

  /** DynamoDb Number, which is represented as a string
    */
  case class Number(value: String)
  object Number {
    def of[A: Numeric](a: A): Number = Number(a.toString)
  }

  val nul: DynamoValue =
    make(_.nul(true))

  def s(value: String): DynamoValue =
    make(_.s(value))

  def bool(value: Boolean): DynamoValue =
    make(_.bool(value))

  def n(value: Number): DynamoValue =
    make(_.n(value.value))

  def n[A: Numeric](value: A): DynamoValue =
    n(Number.of(value))

  def m(values: Map[String, DynamoValue]): DynamoValue =
    make(_.m { values.map { case (k, v) => k -> v.value }.asJava })

  def m(values: (String, DynamoValue)*): DynamoValue =
    m(values.toMap)

  def l(values: List[DynamoValue]): DynamoValue =
    make(_.l(values.map(_.value).asJava))

  def l(values: DynamoValue*): DynamoValue =
    l(values.toList)

  def ss(values: NonEmptySet[String]): DynamoValue =
    make(_.ss(values.value.toList.asJava))

  def ns(values: NonEmptySet[Number]): DynamoValue =
    make(_.ns(values.value.toList.map(_.value).asJava))

  def bs(values: NonEmptySet[ByteVector]): DynamoValue =
    make {
      _.bs {
        values.value.toList
          .map(bv => SdkBytes.fromByteArray(bv.toArray))
          .asJava
      }
    }

  def b(value: ByteVector): DynamoValue =
    make(_.b(SdkBytes.fromByteArray(value.toArray)))
}

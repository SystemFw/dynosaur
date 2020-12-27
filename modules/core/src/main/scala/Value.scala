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

import collection.JavaConverters._ // TODO generates a warning, since it's deprecated for
//import scala.jdk.CollectionConverters._

// TODO file to model.scala, DynamoValue, DynamoNumber
case class Value(value: AttributeValue) {

  def s: Option[String] =
    Option(value.s)

  def n: Option[Value.Number] =
    Option(value.n).map(Value.Number(_))

  def bool: Option[Boolean] =
    Option(value.bool).map(_.booleanValue)

  def l: Option[List[Value]] =
    value.hasL
      .guard[Option]
      .as(value.l.asScala.toList.map(Value(_)))

  def m: Option[Map[String, Value]] =
    value.hasM
      .guard[Option]
      .as(value.m.asScala.toMap.map { case (k, v) => k -> Value(v) })

  // TODO should Boolean result be supported here?
  def nul: Option[Unit] =
    Option(value.nul).void

  def b: Option[ByteVector] =
    Option(value.b).map(b => ByteVector(b.asByteArray))

  def bs: Option[NonEmptySet[ByteVector]] =
    value.hasBs
      .guard[Option] >> NonEmptySet.fromSet(
      value.bs.asScala.toSet.map((b: SdkBytes) => ByteVector(b.asByteArray))
    )

  def ns: Option[NonEmptySet[Value.Number]] =
    value.hasNs
      .guard[Option] >> NonEmptySet.fromSet(
      value.ns.asScala.toSet.map((x: String) => Value.Number(x))
    )

  def ss: Option[NonEmptySet[String]] =
    value.hasSs.guard[Option] >> NonEmptySet.fromSet(value.ss.asScala.toSet)
}
object Value {
  def make(build: AttributeValue.Builder => AttributeValue.Builder): Value =
    Value(build(AttributeValue.builder).build)

  // TODO from 2.13 on, Numeric has a parseFromString
  /** DynamoDb Number, which is represented as a string
    */
  case class Number(value: String)

  // TODO should Boolean result be supported here?
  val nul: Value =
    make(_.nul(true))

  def s(value: String): Value =
    make(_.s(value))

  def bool(value: Boolean): Value =
    make(_.bool(value))

  def n(value: Number): Value =
    make(_.n(value.value))

  def m(values: Map[String, Value]): Value =
    make(_.m { values.map { case (k, v) => k -> v.value }.asJava })

  def m(values: (String, Value)*): Value =
    m(values.toMap)

  def l(values: List[Value]): Value =
    make(_.l(values.map(_.value).asJava))

  def l(values: Value*): Value =
    l(values.toList)

  def ss(values: NonEmptySet[String]): Value =
    make(_.ss(values.value.toList.asJava))

  def ns(values: NonEmptySet[Number]): Value =
    make(_.ns(values.value.toList.map(_.value).asJava))

  def bs(values: NonEmptySet[ByteVector]): Value =
    make {
      _.bs {
        values.value.toList
          .map(bv => SdkBytes.fromByteArray(bv.toArray))
          .asJava
      }
    }

  def b(value: ByteVector): Value =
    make(_.b(SdkBytes.fromByteArray(value.toArray)))
}

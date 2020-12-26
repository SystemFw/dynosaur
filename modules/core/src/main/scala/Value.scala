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
import scala.collection.immutable
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.core.SdkBytes

import collection.JavaConverters._ // TODO generates a warning, since it's deprecated for
//import scala.jdk.CollectionConverters._

// SdkBytes	b()
// Boolean	bool()
// String	n()
// List<SdkBytes>	bs()
// List<String>	ss()
// List<String>	ns()
// Boolean	nul()
// String	s()

// List<AttributeValue>	l()
// Map<String,AttributeValue>	m()

// boolean	hasBs()
// boolean	hasNs()
// boolean	hasSs()
// boolean	hasL()
// boolean	hasM()

// static AttributeValue.Builder	builder()
// boolean	equals(Object obj)
// boolean	equalsBySdkFields(Object obj)
// <T> Optional<T>	getValueForField(String fieldName, Class<T> clazz)
// int	hashCode()

// List<SdkField<?>>	sdkFields()
// static Class<? extends AttributeValue.Builder>	serializableBuilderClass()

// AttributeValue.Builder	toBuilder()
// String	toString()

// TODO file to model.scala, DynamoValue, DynamoNumber
case class Value(value: AttributeValue) {

  def s: Option[String] =
    Option(value.s)

  def n: Option[Value.Number] =
    Option(value.n).map(Value.Number(_))

  def bool: Option[Boolean] =
    Option(value.bool).map(_.booleanValue)

  // TODO replace with list throughout
  def l: Option[Vector[Value]] =
    value.hasL
      .guard[Option]
      .as(value.l.asScala.toVector.map(Value(_)))

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

  /** DynamoDb Number, which is represented as a string
    */
  case class Number(value: String)

  // case object Nul extends Value
  // case class S(value: String) extends Value
  // case class N(value: String) extends Value
  // case class B(value: ByteVector) extends Value
  // case class Bool(value: Boolean) extends Value
  // case class M(values: Map[String, Value]) extends Value
  // case class L(values: Vector[Value]) extends Value
  // case class SS(values: NonEmptySet[String]) extends Value
  // case class NS(values: NonEmptySet[String]) extends Value
  // case class BS(values: NonEmptySet[ByteVector]) extends Value

  val nul: Value = ???

  def m(values: (String, Value)*): Value = ???
//    Value.M(values.toMap)
  def m(values: Map[String, Value]): Value = ???
  //  Value.M(values)

  def s(value: String): Value = ??? // Value.S(value)

  def ss(values: NonEmptySet[String]): Value =
    ??? //    Value.SS(values)
  def ss(value: String, values: String*): Value =
    ??? //    Value.SS(NonEmptySet.of(value, values: _*))

  def ns(values: NonEmptySet[Number]): Value =
    ???
//  def ns(value: Number, values: Number*): Value = ??? TODO delete

  def bs(values: NonEmptySet[ByteVector]): Value =
    ???
  def bs(value: ByteVector, values: ByteVector*): Value = ???

  def n(value: Number): Value = ??? // Value.N(value.toString)
  // def n(value: Long): Value = ??? // Value.N(value.toString)
  // def n(value: Double): Value = ??? // Value.N(value.toString)
  // def n(value: Float): Value = ??? // Value.N(value.toString)
  // def n(value: Short): Value = ??? // Value.N(value.toString)

  def b(value: ByteVector): Value = ??? //Value.B(value)
  def b(value: Array[Byte]): Value =
    ??? //    Value.B(ByteVector(value))
  def b(value: immutable.Seq[Byte]): Value =
    ??? //    Value.B(ByteVector(value))

  def bool(value: Boolean): Value = ??? // Value.Bool(value)

  def l(values: immutable.Seq[Value]): Value =
    ??? //    Value.L(values.toVector)

//   AttributeValue.Builder	b(SdkBytes b)
// AttributeValue.Builder	bool(Boolean bool)
// AttributeValue.Builder	bs(Collection<SdkBytes> bs)
// AttributeValue.Builder	bs(SdkBytes... bs)
// AttributeValue.Builder	l(AttributeValue... l)
// AttributeValue.Builder	l(Collection<AttributeValue> l)
// AttributeValue.Builder	l(Consumer<AttributeValue.Builder>... l)
// AttributeValue.Builder	m(Map<String,AttributeValue> m)
// AttributeValue.Builder	n(String n)
// AttributeValue.Builder	ns(Collection<String> ns)
// AttributeValue.Builder	ns(String... ns)
// AttributeValue.Builder	nul(Boolean nul)
// AttributeValue.Builder	s(String s)
// AttributeValue.Builder	ss(Collection<String> ss)
// AttributeValue.Builder	ss(String... ss)
}

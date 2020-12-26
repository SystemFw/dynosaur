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

sealed trait Value {
  def nul: Option[Value.Nul.type] = this match {
    case Value.Nul => Value.Nul.some
    case _ => None
  }

  def s: Option[Value.S] = this match {
    case v @ Value.S(_) => v.some
    case _ => None
  }

  def n: Option[Value.N] = this match {
    case v @ Value.N(_) => v.some
    case _ => None
  }

  def b: Option[Value.B] = this match {
    case v @ Value.B(_) => v.some
    case _ => None
  }

  def bool: Option[Value.Bool] = this match {
    case v @ Value.Bool(_) => v.some
    case _ => None
  }

  def m: Option[Value.M] = this match {
    case v @ Value.M(_) => v.some
    case _ => None
  }

  def l: Option[Value.L] = this match {
    case v @ Value.L(_) => v.some
    case _ => None
  }

  def ss: Option[Value.SS] = this match {
    case v @ Value.SS(_) => v.some
    case _ => None
  }

  def ns: Option[Value.NS] = this match {
    case v @ Value.NS(_) => v.some
    case _ => None
  }

  def bs: Option[Value.BS] = this match {
    case v @ Value.BS(_) => v.some
    case _ => None
  }

}
object Value {
  case object Nul extends Value
  case class S(value: String) extends Value
  case class N(value: String) extends Value
  case class B(value: ByteVector) extends Value
  case class Bool(value: Boolean) extends Value
  case class M(values: Map[String, Value]) extends Value
  case class L(values: Vector[Value]) extends Value
  case class SS(values: NonEmptySet[String]) extends Value
  case class NS(values: NonEmptySet[String]) extends Value
  case class BS(values: NonEmptySet[ByteVector]) extends Value

  val nul: Value = Nul

  implicit val monoid: Monoid[Value.M] =
    new Monoid[Value.M] {
      def empty: Value.M =
        M(Map.empty)
      def combine(
          x: Value.M,
          y: Value.M
      ): Value.M =
        M(x.values ++ y.values)
    }

  def m(values: (String, Value)*): Value =
    Value.M(values.toMap)
  def m(values: Map[String, Value]): Value =
    Value.M(values)

  def s(value: String): Value = Value.S(value)

  def ss(values: NonEmptySet[String]): Value =
    Value.SS(values)
  def ss(value: String, values: String*): Value =
    Value.SS(NonEmptySet.of(value, values: _*))

  def n(value: Int): Value = Value.N(value.toString)
  def n(value: Long): Value = Value.N(value.toString)
  def n(value: Double): Value = Value.N(value.toString)
  def n(value: Float): Value = Value.N(value.toString)
  def n(value: Short): Value = Value.N(value.toString)

  def b(value: ByteVector): Value = Value.B(value)
  def b(value: Array[Byte]): Value =
    Value.B(ByteVector(value))
  def b(value: immutable.Seq[Byte]): Value =
    Value.B(ByteVector(value))

  def bool(value: Boolean): Value = Value.Bool(value)

  def l(values: immutable.Seq[Value]): Value =
    Value.L(values.toVector)
}

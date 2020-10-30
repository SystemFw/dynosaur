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
package model

import cats._, implicits._, data._
import scodec.bits.ByteVector
import scala.collection.immutable

/**
  * TODO
  * 1) I think I like slightly more descriptive names for the cases of the ADT,
  * even if they don't mirror the Dynamo spec 1-to-1, we could add the correspondence
  * in the scaladoc.
  *
  * 2) We need to figure out what the hell to do with numbers in terms
  * of the matchers. However using String like we do now works well
  * with the Schema approach, so maybe what we have is good.
  *
  * 3) Add a zipper, possibly reusing the one from circe, to get errors
  *
  * 4) could maybe use typeclasses for the numbers overloads, and similarly to fill the gap
  *    on bs and ns constructors
  *
  */
case class AttributeName(value: String)

case class AttributeRef(path: NonEmptyList[AttributeName])

sealed trait AttributeValue {
  def `null`: Option[AttributeValue.NULL.type] = this match {
    case AttributeValue.NULL => AttributeValue.NULL.some
    case _ => None
  }

  def s: Option[AttributeValue.S] = this match {
    case v @ AttributeValue.S(_) => v.some
    case _ => None
  }

  def n: Option[AttributeValue.N] = this match {
    case v @ AttributeValue.N(_) => v.some
    case _ => None
  }

  def b: Option[AttributeValue.B] = this match {
    case v @ AttributeValue.B(_) => v.some
    case _ => None
  }

  def bool: Option[AttributeValue.BOOL] = this match {
    case v @ AttributeValue.BOOL(_) => v.some
    case _ => None
  }

  def m: Option[AttributeValue.M] = this match {
    case v @ AttributeValue.M(_) => v.some
    case _ => None
  }

  def l: Option[AttributeValue.L] = this match {
    case v @ AttributeValue.L(_) => v.some
    case _ => None
  }

  def ss: Option[AttributeValue.SS] = this match {
    case v @ AttributeValue.SS(_) => v.some
    case _ => None
  }

  def ns: Option[AttributeValue.NS] = this match {
    case v @ AttributeValue.NS(_) => v.some
    case _ => None
  }

  def bs: Option[AttributeValue.BS] = this match {
    case v @ AttributeValue.BS(_) => v.some
    case _ => None
  }

}
object AttributeValue {
  case object NULL extends AttributeValue
  case class S(value: String) extends AttributeValue
  case class N(value: String) extends AttributeValue
  case class B(value: ByteVector) extends AttributeValue
  case class BOOL(value: Boolean) extends AttributeValue
  case class M(values: Map[AttributeName, AttributeValue])
      extends AttributeValue
  case class L(values: Vector[AttributeValue]) extends AttributeValue
  case class SS(values: NonEmptySet[String]) extends AttributeValue
  case class NS(values: NonEmptySet[String]) extends AttributeValue
  case class BS(values: NonEmptySet[ByteVector]) extends AttributeValue

  val `null`: AttributeValue = NULL

  implicit val monoid: Monoid[AttributeValue.M] =
    new Monoid[AttributeValue.M] {
      def empty: AttributeValue.M =
        M(Map.empty)
      def combine(
          x: AttributeValue.M,
          y: AttributeValue.M
      ): AttributeValue.M =
        M(x.values ++ y.values)
    }

  def m(values: (AttributeName, AttributeValue)*): AttributeValue =
    AttributeValue.M(values.toMap)
  def m(values: Map[AttributeName, AttributeValue]): AttributeValue =
    AttributeValue.M(values)

  def s(value: String): AttributeValue = AttributeValue.S(value)

  def ss(values: NonEmptySet[String]): AttributeValue =
    AttributeValue.SS(values)
  def ss(value: String, values: String*): AttributeValue =
    AttributeValue.SS(NonEmptySet(value, values.toSet))

  def n(value: Int): AttributeValue = AttributeValue.N(value.toString)
  def n(value: Long): AttributeValue = AttributeValue.N(value.toString)
  def n(value: Double): AttributeValue = AttributeValue.N(value.toString)
  def n(value: Float): AttributeValue = AttributeValue.N(value.toString)
  def n(value: Short): AttributeValue = AttributeValue.N(value.toString)

  def b(value: ByteVector): AttributeValue = AttributeValue.B(value)
  def b(value: Array[Byte]): AttributeValue =
    AttributeValue.B(ByteVector(value))
  def b(value: immutable.Seq[Byte]): AttributeValue =
    AttributeValue.B(ByteVector(value))

  def bool(value: Boolean): AttributeValue = AttributeValue.BOOL(value)

  def l(values: immutable.Seq[AttributeValue]): AttributeValue =
    AttributeValue.L(values.toVector)
}

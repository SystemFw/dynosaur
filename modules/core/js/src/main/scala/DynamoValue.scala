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

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array

import cats._, syntax.all._
import org.typelevel.paiges.Doc
import scodec.bits.ByteVector

import dynosaur.CollectionConverters.all._

case class DynamoValue(value: AttributeValue) {

  lazy val s: Option[String] =
    value.S.toOption

  lazy val n: Option[DynamoValue.Number] =
    value.N.toOption.map(DynamoValue.Number(_))

  lazy val bool: Option[Boolean] =
    value.BOOL.toOption.map(_.booleanValue)

  lazy val l: Option[List[DynamoValue]] =
    value.L.toOption
      .map(_.toList.map(DynamoValue(_)))

  lazy val m: Option[Map[String, DynamoValue]] =
    value.M.toOption
      .map(_.toMap.map { case (k, v) => k -> DynamoValue(v) })

  lazy val nul: Option[Unit] =
    value.NULL.toOption.void

  lazy val b: Option[ByteVector] =
    value.B.toOption.map { value =>
      ByteVector(value.toList.map(_.toByte))
    }

  lazy val bs: Option[NonEmptySet[ByteVector]] =
    value.BS.toOption
      .map { xs: js.Array[Uint8Array] =>
        xs.toVector.map { x =>
          ByteVector(x.toList.map(_.toByte))
        }
      }
      .flatMap { bs =>
        NonEmptySet.fromSet(bs.toList.toSet)
      }

  lazy val ns: Option[NonEmptySet[DynamoValue.Number]] =
    value.NS.toOption
      .flatMap { ns =>
        NonEmptySet.fromSet(ns.toSet.map(DynamoValue.Number(_)))
      }

  lazy val ss: Option[NonEmptySet[String]] =
    value.SS.toOption
      .flatMap { ns =>
        NonEmptySet.fromSet(ns.toSet)
      }

  /** Converts a DynamoValue to an AWS SDK-compatible attribute map, e.g. for a
    * PutItem operation. Returns None if the value isn't backed by a map.
    */
  lazy val attributeMap: Option[Map[String, AttributeValue]] =
    value.M.toOption.map(_.toMap)

  def fold[A](
      s: String => A,
      n: DynamoValue.Number => A,
      bool: Boolean => A,
      l: List[DynamoValue] => A,
      m: Map[String, DynamoValue] => A,
      nul: Unit => A,
      b: ByteVector => A,
      bs: NonEmptySet[ByteVector] => A,
      ns: NonEmptySet[DynamoValue.Number] => A,
      ss: NonEmptySet[String] => A
  ): A = {
    this.s.map(s) <+>
      this.n.map(n) <+>
      this.bool.map(bool) <+>
      this.l.map(l) <+>
      this.m.map(m) <+>
      this.nul.map(nul) <+>
      this.b.map(b) <+>
      this.bs.map(bs) <+>
      this.ns.map(ns) <+>
      this.ss.map(ss)
  }.getOrElse(throw new RuntimeException(s"It is null: ${toDoc}"))

  override def toString: String = {
    val str = print(40)
    if (str.contains("\n")) s"\n$str\n" else str
  }

  def print(maxLength: Int): String =
    toDoc.render(maxLength)

  def canEqual(that: Any) = that.isInstanceOf[DynamoValue]

  override def equals(that: Any) = that match {
    case that: DynamoValue =>
      toDoc == that.toDoc
    case _ => false
  }

  override def hashCode() = toDoc.hashCode

  val lazyToDoc = Eval.later {

    implicit class ToDoc(s: String) {
      def t = Doc.text(s)
      def colon(d: Doc) = s.t.quotes + ":".t & d
    }
    implicit class Ops(d: Doc) {
      def brackets = d.bracketBy("{".t, "}".t)
      def squareBrackets = d.bracketBy("[".t, "]".t)
      def quotes = "\"".t + d + "\"".t
    }

    def csv(d: Iterable[Doc]) =
      Doc.intercalate(Doc.comma + Doc.line, d)

    def strings(s: String) =
      "S".colon(s.t.quotes)

    def numbers(n: DynamoValue.Number) =
      "N".colon(n.value.t.quotes)

    def binaries(b: ByteVector) =
      "B".colon(b.toBase64.t.quotes)

    def bools(bool: Boolean) =
      "BOOL".colon(Doc.str(bool))

    def nuls =
      "NULL".colon(Doc.str(true))

    def lists(l: List[DynamoValue]) =
      "L".colon(csv(l.map(_.toDoc.brackets)).squareBrackets)

    def maps(m: Map[String, DynamoValue]) =
      "M".colon {
        csv {
          m.map { case (k, v) => k.colon(v.toDoc.brackets) }
        }.brackets
      }

    def stringSets(ss: NonEmptySet[String]) =
      "SS".colon(csv(ss.value.map(_.t.quotes)).squareBrackets)

    def numberSets(ns: NonEmptySet[DynamoValue.Number]) =
      "NS".colon(csv(ns.value.map(_.value.t.quotes)).squareBrackets)

    def binarySets(bs: NonEmptySet[ByteVector]) =
      "BS".colon(csv(bs.value.map(_.toBase64.t.quotes)).squareBrackets)

    this.fold(
      strings,
      numbers,
      bools,
      lists,
      maps,
      _ => nuls,
      binaries,
      binarySets,
      numberSets,
      stringSets
    )
  }.memoize

  lazy val toDoc: Doc = lazyToDoc.value

}
object DynamoValue {

  /** DynamoDb Number, which is represented as a string
    */
  case class Number(value: String)
  object Number {
    def of[A: Numeric](a: A): Number = {
      // to avoid unused value warning
      val _ = implicitly[Numeric[A]]
      Number(a.toString)
    }
  }

  val nul: DynamoValue = DynamoValue(AttributeValue.NULL)

  def s(value: String): DynamoValue = DynamoValue(AttributeValue.S(value))

  def bool(value: Boolean): DynamoValue = DynamoValue(
    AttributeValue.BOOL(value)
  )

  def n(value: Number): DynamoValue = DynamoValue(AttributeValue.N(value.value))

  def n[A: Numeric](value: A): DynamoValue =
    n(Number.of(value))

  def m(values: Map[String, DynamoValue]): DynamoValue =
    DynamoValue(
      AttributeValue.M(values.map { case (k, v) =>
        (k, v.value)
      }.toJSDictionary)
    )

  def m(values: (String, DynamoValue)*): DynamoValue =
    m(values.toMap)

  def l(values: List[DynamoValue]): DynamoValue = DynamoValue(
    AttributeValue.L(values.map(_.value).toJSArray)
  )

  def l(values: DynamoValue*): DynamoValue =
    l(values.toList)

  def ss(values: NonEmptySet[String]): DynamoValue = DynamoValue(
    AttributeValue.SS(values.value.toJSArray)
  )

  def ns(values: NonEmptySet[Number]): DynamoValue = DynamoValue(
    AttributeValue.NS(values.value.map(_.value).toJSArray)
  )

  def bs(values: NonEmptySet[ByteVector]): DynamoValue = DynamoValue(
    AttributeValue.BS(
      values.value.map { b =>
        new Uint8Array(b.toArray.map(_.toShort).toJSArray)
      }.toJSArray
    )
  )

  def b(value: ByteVector): DynamoValue = DynamoValue(
    AttributeValue.B(new Uint8Array(value.toArray.map(_.toShort).toJSArray))
  )

  // /** Builds a DynamoValue from an AWS SDK-compatible attribute map, e.g. from a
  //   * GetItem response.
  //   */
  // def attributeMap(
  //     attributes: java.util.Map[String, AttributeValue]
  // ): DynamoValue = make(_.m(attributes))

}

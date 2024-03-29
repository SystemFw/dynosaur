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
import org.typelevel.paiges.Doc
import scodec.bits.ByteVector
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue => AwsSdkAttributeValue
}
import dynosaur.CollectionConverters.all._

case class DynamoValue(value: AttributeValue) {

  lazy val s: Option[String] =
    Option(value.s)

  lazy val n: Option[DynamoValue.Number] =
    Option(value.n).map(DynamoValue.Number(_))

  lazy val bool: Option[Boolean] =
    Option(value.bool).map(_.booleanValue)

  lazy val l: Option[List[DynamoValue]] =
    value.hasL
      .guard[Option]
      .as(value.l.asScala.toList.map(DynamoValue(_)))

  lazy val m: Option[Map[String, DynamoValue]] =
    value.hasM
      .guard[Option]
      .as(value.m.asScala.toMap.map { case (k, v) => k -> DynamoValue(v) })

  lazy val nul: Option[Unit] =
    Option(value.nul).void

  lazy val b: Option[ByteVector] =
    Option(value.b).map(b => ByteVector(b.asByteArray))

  lazy val bs: Option[NonEmptySet[ByteVector]] =
    value.hasBs
      .guard[Option] >> NonEmptySet.fromSet(
      value.bs.asScala.toSet.map((b: SdkBytes) => ByteVector(b.asByteArray))
    )

  lazy val ns: Option[NonEmptySet[DynamoValue.Number]] =
    value.hasNs
      .guard[Option] >> NonEmptySet.fromSet(
      value.ns.asScala.toSet.map(DynamoValue.Number(_))
    )

  lazy val ss: Option[NonEmptySet[String]] =
    value.hasSs.guard[Option] >> NonEmptySet.fromSet(value.ss.asScala.toSet)

  /** Converts a DynamoValue to an AWS SDK-compatible attribute map, e.g. for a
    * PutItem operation. Returns None if the value isn't backed by a map.
    */
  lazy val attributeMap: Option[java.util.Map[String, AttributeValue]] =
    value.hasM.guard[Option].as(value.m)

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
  }.get

  override def toString: String = {
    val str = print(40)
    if (str.contains("\n")) s"\n$str\n" else str
  }

  def print(maxLength: Int): String =
    toDoc.render(maxLength)

  def toDoc: Doc = {
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
          m.toList.sortBy(_._1).map { case (k, v) => k.colon(v.toDoc.brackets) }
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
  }

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

  /** Builds a DynamoValue from an AWS SDK-compatible attribute map, e.g. from a
    * GetItem response.
    */
  def attributeMap(
      attributes: java.util.Map[String, AttributeValue]
  ): DynamoValue = make(_.m(attributes))

  private def make(
      build: AwsSdkAttributeValue.Builder => AwsSdkAttributeValue.Builder
  ): DynamoValue =
    DynamoValue(build(AwsSdkAttributeValue.builder).build)
}

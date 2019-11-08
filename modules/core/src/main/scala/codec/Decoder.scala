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
package codec

import cats._, implicits._
import alleycats.std.map._
import cats.free.Free
import cats.data.Chain
import scodec.bits.ByteVector

import model.{AttributeName, AttributeValue}
import Schema.structure._

case class ReadError() extends Exception

trait Decoder[A] {
  def read(v: AttributeValue): Either[ReadError, A]
}
object Decoder {
  def instance[A](f: AttributeValue => Either[ReadError, A]): Decoder[A] =
    new Decoder[A] {
      def read(v: AttributeValue) = f(v)
    }

  def fromSchema[A](s: Schema[A]): Decoder[A] = {
    type Res[B] = Either[ReadError, B]

    def decodeBool: AttributeValue => Res[Boolean] =
      _.bool.toRight(ReadError()).map(_.value)

    def decodeNum: AttributeValue => Res[String] =
      _.n.toRight(ReadError()).map(_.value)

    def decodeString: AttributeValue => Res[String] =
      _.s.toRight(ReadError()).map(_.value)

    def decodeBytes: AttributeValue => Res[ByteVector] =
      _.b.toRight(ReadError()).map(_.value)

    def decodeNullable[V](
        schema: Schema[V],
        v: AttributeValue
    ): Res[Option[V]] =
      v.`null`
        .toRight(ReadError())
        .as(none[V])
        .handleErrorWith(_ => fromSchema(schema).read(v).map(_.some))

    def decodeSequence[V](
        schema: Schema[V],
        value: AttributeValue
    ): Res[Vector[V]] =
      value.l
        .toRight(ReadError())
        .flatMap(_.values.traverse(fromSchema(schema).read))

    def decodeDictionary[V](
        schema: Schema[V],
        value: AttributeValue
    ): Res[Map[String, V]] =
      value.m
        .toRight(ReadError())
        .flatMap(
          _.values
            .map { case (k, v) => k.value -> v }
            .traverse(fromSchema(schema).read)
        )

    def decodeRecord[R](
        recordSchema: Free[Field[R, ?], R],
        v: AttributeValue.M
    ): Res[R] =
      recordSchema.foldMap {
        λ[Field[R, ?] ~> Res] {
          case field: Field.Required[R, e] =>
            v.values
              .get(AttributeName(field.name))
              .toRight(ReadError())
              .flatMap { v =>
                fromSchema(field.elemSchema).read(v)
              }
          case field: Field.Optional[R, e] =>
            v.values
              .get(AttributeName(field.name))
              .traverse { v =>
                fromSchema(field.elemSchema).read(v)
              }
        }
      }

    def decodeSum[B](cases: Chain[Alt[B]], v: AttributeValue): Res[B] =
      cases
        .foldMapK { alt =>
          fromSchema(alt.caseSchema).read(v).map(alt.prism.inject).toOption
        }
        .toRight(ReadError())

    def decodeIsos[V](xmap: XMap[V], v: AttributeValue): Res[V] =
      fromSchema(xmap.schema)
        .read(v)
        .flatMap(xmap.r)

    s match {
      case Identity => Decoder.instance(_.asRight)
      case Num => Decoder.instance(decodeNum)
      case Str => Decoder.instance(decodeString)
      case Bool => Decoder.instance(decodeBool)
      case Bytes => Decoder.instance(decodeBytes)
      case Nullable(inner) => Decoder.instance(decodeNullable(inner, _))
      case Sequence(elem) => Decoder.instance(decodeSequence(elem, _))
      case Dictionary(elem) => Decoder.instance(decodeDictionary(elem, _))
      case Record(rec) =>
        Decoder.instance {
          _.m.toRight(ReadError()).flatMap(decodeRecord(rec, _))
        }
      case Sum(cases) => Decoder.instance(decodeSum(cases, _))
      case Isos(iso) => Decoder.instance(decodeIsos(iso, _))
    }
  }
}

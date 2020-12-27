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

import cats._, implicits._
import alleycats.std.map._
import cats.free.Free
import cats.data.Chain
import scodec.bits.ByteVector

import Schema.ReadError
import Schema.structure._

trait Decoder[A] {
  def read(v: DynamoValue): Either[ReadError, A]
}
object Decoder {
  def instance[A](f: DynamoValue => Either[ReadError, A]): Decoder[A] =
    new Decoder[A] {
      def read(v: DynamoValue) = f(v)
    }

  def fromSchema[A](s: Schema[A]): Decoder[A] = {
    type Res[B] = Either[ReadError, B]

    def decodeBool: DynamoValue => Res[Boolean] =
      _.bool.toRight(ReadError())

    def decodeNum: DynamoValue => Res[DynamoValue.Number] =
      _.n.toRight(ReadError())

    def decodeString: DynamoValue => Res[String] =
      _.s.toRight(ReadError())

    def decodeBytes: DynamoValue => Res[ByteVector] =
      _.b.toRight(ReadError())

    def decodeBytesSet: DynamoValue => Res[NonEmptySet[ByteVector]] =
      _.bs.toRight(ReadError())

    def decodeNumSet: DynamoValue => Res[NonEmptySet[DynamoValue.Number]] =
      _.ns.toRight(ReadError())

    def decodeStrSet: DynamoValue => Res[NonEmptySet[String]] =
      _.ss.toRight(ReadError())

    def decodeNull: DynamoValue => Res[Unit] =
      _.nul.toRight(ReadError())

    def decodeSequence[V](
        schema: Schema[V],
        value: DynamoValue
    ): Res[List[V]] =
      value.l
        .toRight(ReadError())
        .flatMap(_.traverse(fromSchema(schema).read))

    def decodeDictionary[V](
        schema: Schema[V],
        value: DynamoValue
    ): Res[Map[String, V]] =
      value.m
        .toRight(ReadError())
        .flatMap(
          _.map { case (k, v) => k -> v }
            .traverse(fromSchema(schema).read)
        )

    def decodeRecord[R](
        recordSchema: Free[Field[R, *], R],
        v: Map[String, DynamoValue]
    ): Res[R] =
      recordSchema.foldMap {
        new (Field[R, *] ~> Res) {
          def apply[B](field: Field[R, B]): Res[B] =
            field match {
              case Field.Required(name, elemSchema, _) =>
                v.get(name)
                  .toRight(ReadError())
                  .flatMap { v =>
                    fromSchema(elemSchema).read(v)
                  }
              case Field.Optional(name, elemSchema, _) =>
                v
                  .get(name)
                  .traverse { v =>
                    fromSchema(elemSchema).read(v)
                  }
            }
        }
      }

    def decodeSum[B](cases: Chain[Alt[B]], v: DynamoValue): Res[B] =
      cases
        .foldMapK { alt =>
          fromSchema(alt.caseSchema).read(v).map(alt.prism.inject).toOption
        }
        .toRight(ReadError())

    def decodeIsos[V](xmap: XMap[V], v: DynamoValue): Res[V] =
      fromSchema(xmap.schema)
        .read(v)
        .flatMap(xmap.r)

    s match {
      case Identity => Decoder.instance(_.asRight)
      case Num => Decoder.instance(decodeNum)
      case Str => Decoder.instance(decodeString)
      case Bool => Decoder.instance(decodeBool)
      case Bytes => Decoder.instance(decodeBytes)
      case BytesSet => Decoder.instance(decodeBytesSet)
      case NumSet => Decoder.instance(decodeNumSet)
      case StrSet => Decoder.instance(decodeStrSet)
      case Nul => Decoder.instance(decodeNull)
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

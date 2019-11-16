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
import cats.data.{Chain, WriterT}
import cats.free.Free

import scodec.bits.ByteVector

import model.{AttributeName, AttributeValue, NonEmptySet}
import Schema.structure._

case class WriteError() extends Exception

trait Encoder[A] {
  def write(a: A): Either[WriteError, AttributeValue]
}
object Encoder {
  def instance[A](f: A => Either[WriteError, AttributeValue]): Encoder[A] =
    new Encoder[A] {
      def write(a: A) = f(a)
    }

  def fromSchema[A](s: Schema[A]): Encoder[A] = {
    type Res = Either[WriteError, AttributeValue]

    def encodeBool: Boolean => Res = AttributeValue.bool(_).asRight

    def encodeNum: String => Res = AttributeValue.N(_).asRight

    def encodeString: String => Res = AttributeValue.s(_).asRight

    def encodeBytes: ByteVector => Res = AttributeValue.b(_).asRight

    def encodeBytesSet: NonEmptySet[ByteVector] => Res =
      AttributeValue.BS(_).asRight
    def encodeNumSet: NonEmptySet[String] => Res =
      AttributeValue.NS(_).asRight
    def encodeStrSet: NonEmptySet[String] => Res =
      AttributeValue.ss(_).asRight

    def encodeNull: Unit => Res = _ => AttributeValue.`null`.asRight

    def encodeSequence[V](schema: Schema[V], value: Vector[V]) =
      value.traverse(fromSchema(schema).write).map(AttributeValue.L)

    def encodeDictionary[V](schema: Schema[V], value: Map[String, V]) =
      value
        .map { case (k, v) => AttributeName(k) -> v }
        .traverse(fromSchema(schema).write)
        .map(AttributeValue.M)

    def encodeRecord[R](recordSchema: Free[Field[R, ?], R], record: R): Res = {
      def write[E](name: String, schema: Schema[E])(elem: E) =
        fromSchema(schema).write(elem).map { av =>
          AttributeValue.M(Map(AttributeName(name) -> av))
        }

      recordSchema
        .foldMap {
          λ[Field[R, ?] ~> WriterT[Either[WriteError, ?], AttributeValue.M, ?]] {
            case field: Field.Required[R, e] =>
              WriterT {
                val elem: e = field.get(record)
                write(field.name, field.elemSchema)(elem).tupleRight(elem)
              }
            case field: Field.Optional[R, e] =>
              WriterT {
                val elem: Option[e] = field.get(record)
                elem
                  .foldMap(write(field.name, field.elemSchema))
                  .tupleRight(elem)
              }
          }
        }
        .written
        .widen[AttributeValue]
    }

    def encodeSum[C](cases: Chain[Alt[C]], coproduct: C): Res =
      cases
        .foldMapK { alt =>
          alt.prism.tryGet(coproduct).map { elem =>
            fromSchema(alt.caseSchema).write(elem)
          }
        }
        .getOrElse(WriteError().asLeft)

    def encodeIsos[V](xmap: XMap[V], value: V): Res =
      xmap.w(value).flatMap(v => fromSchema(xmap.schema).write(v))

    s match {
      case Identity => Encoder.instance(_.asRight)
      case Num => Encoder.instance(encodeNum)
      case Str => Encoder.instance(encodeString)
      case Bool => Encoder.instance(encodeBool)
      case Bytes => Encoder.instance(encodeBytes)
      case BytesSet => Encoder.instance(encodeBytesSet)
      case NumSet => Encoder.instance(encodeNumSet)
      case StrSet => Encoder.instance(encodeStrSet)
      case NULL => Encoder.instance(encodeNull)
      case Sequence(elem) => Encoder.instance(encodeSequence(elem, _))
      case Dictionary(elem) => Encoder.instance(encodeDictionary(elem, _))
      case Record(rec) => Encoder.instance(encodeRecord(rec, _))
      case Sum(cases) => Encoder.instance(encodeSum(cases, _))
      case Isos(iso) => Encoder.instance(encodeIsos(iso, _))
    }
  }
}

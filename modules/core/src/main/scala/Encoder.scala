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
import cats.data.{Chain, WriterT}
import cats.free.Free

import scodec.bits.ByteVector

import Schema.structure._

case class WriteError() extends Exception

trait Encoder[A] {
  def write(a: A): Either[WriteError, Value]
}
object Encoder {
  def instance[A](f: A => Either[WriteError, Value]): Encoder[A] =
    new Encoder[A] {
      def write(a: A) = f(a)
    }

  def fromSchema[A](s: Schema[A]): Encoder[A] = {
    type Res = Either[WriteError, Value]

    def encodeBool: Boolean => Res = Value.bool(_).asRight

    def encodeNum: Value.Number => Res = Value.n(_).asRight

    def encodeString: String => Res = Value.s(_).asRight

    def encodeBytes: ByteVector => Res = Value.b(_).asRight

    def encodeBytesSet: NonEmptySet[ByteVector] => Res =
      Value.bs(_).asRight
    def encodeNumSet: NonEmptySet[Value.Number] => Res =
      Value.ns(_).asRight
    def encodeStrSet: NonEmptySet[String] => Res =
      Value.ss(_).asRight

    def encodeNull: Unit => Res = _ => Value.nul.asRight

    def encodeSequence[V](schema: Schema[V], value: Vector[V]) =
      value.traverse(fromSchema(schema).write).map(Value.l)

    def encodeDictionary[V](schema: Schema[V], value: Map[String, V]) =
      value
        .map { case (k, v) => k -> v }
        .traverse(fromSchema(schema).write)
        .map(Value.m)

    def encodeRecord[R](recordSchema: Free[Field[R, *], R], record: R): Res = {
      implicit def overrideKeys: Monoid[Map[String, Value]] =
        MonoidK[Map[String, *]].algebra

      def write[E](
          name: String,
          schema: Schema[E],
          elem: E
      ): Either[WriteError, Map[String, Value]] =
        fromSchema(schema).write(elem).map { av => Map(name -> av) }

      recordSchema
        .foldMap {
          new (Field[R, *] ~> WriterT[
            Either[WriteError, *],
            Map[String, Value],
            *
          ]) {
            def apply[B](field: Field[R, B]) = field match {
              case Field.Required(name, elemSchema, get) =>
                WriterT {
                  val elem = get(record)
                  write(name, elemSchema, elem).tupleRight(elem)
                }
              case Field.Optional(name, elemSchema, get) =>
                WriterT {
                  val elem = get(record)
                  elem
                    .foldMap(write(name, elemSchema, _))
                    .tupleRight(elem)
                }
            }
          }
        }
        .written
        .map(Value.m)
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

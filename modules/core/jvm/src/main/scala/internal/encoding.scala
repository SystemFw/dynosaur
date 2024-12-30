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
package internal

import cats.{~>, Monoid, MonoidK}
import cats.syntax.all._
import alleycats.std.map._
import cats.data.Chain
import cats.free.FreeApplicative

import scodec.bits.ByteVector

import Schema.WriteError
import Schema.structure._

object encoding {
  def fromSchema[A](s: Schema[A]): A => Either[WriteError, DynamoValue] =
    s match {
      case Identity => (_: DynamoValue).asRight
      case Num => encodeNum
      case Str => encodeString
      case Bool => encodeBool
      case Bytes => encodeBytes
      case BytesSet => encodeBytesSet
      case NumSet => encodeNumSet
      case StrSet => encodeStrSet
      case Nul => encodeNull
      case Sequence(elem) => encodeSequence(elem, _)
      case Dictionary(elem) => encodeDictionary(elem, _)
      case r: Record[A] => encodeRecord(r)
      case Sum(cases) => encodeSum(cases)
      case Isos(iso) => encodeIsos(iso, _)
      case Defer(schema) => schema().write
    }

  type Res = Either[WriteError, DynamoValue]

  def encodeBool: Boolean => Res = DynamoValue.bool(_).asRight

  def encodeNum: DynamoValue.Number => Res = DynamoValue.n(_).asRight

  def encodeString: String => Res = DynamoValue.s(_).asRight

  def encodeBytes: ByteVector => Res = DynamoValue.b(_).asRight

  def encodeBytesSet: NonEmptySet[ByteVector] => Res =
    DynamoValue.bs(_).asRight
  def encodeNumSet: NonEmptySet[DynamoValue.Number] => Res =
    DynamoValue.ns(_).asRight
  def encodeStrSet: NonEmptySet[String] => Res =
    DynamoValue.ss(_).asRight

  def encodeNull: Unit => Res = _ => DynamoValue.nul.asRight

  def encodeSequence[V](schema: Schema[V], value: List[V]) =
    value.traverse(schema.write).map(DynamoValue.l)

  def encodeDictionary[V](schema: Schema[V], value: Map[String, V]) =
    value
      .map { case (k, v) => k -> v }
      .traverse(schema.write)
      .map(DynamoValue.m)

  def encodeSum[C](cases: Chain[Alt[C]]): C => Res = {
    implicit def orElse[T]: Monoid[Option[T]] =
      MonoidK[Option].algebra

    cases
      .foldMap { alt => (coproduct: C) =>
        alt.prism.tryGet(coproduct).map { elem =>
          alt.caseSchema.write(elem)
        }
      }
      .andThen(
        _.getOrElse(
          WriteError(
            "Alternative not specified for all possible subtypes"
          ).asLeft
        )
      )
  }

  def encodeIsos[V](xmap: XMap[V], value: V): Res =
    xmap.w(value).flatMap(v => xmap.schema.write(v))

  def encodeRecord[R](
      record: Record[R]
  ): R => Either[WriteError, DynamoValue] = {
    val fieldCount = record.fields.length
    val fieldNames = record.fieldNames

    { value =>
      {
        var i = 0
        var error: WriteError = null

        val map =
          new java.util.IdentityHashMap[String, AttributeValue](fieldCount)
        while (i < record.fields.length && error == null) {
          val field = record.fields(i)
          val fieldValue = field.get(value)
          field.schema.write(fieldValue.asInstanceOf[field.A]) match {
            case Left(err) => error = err
            case Right(v) => map.put(fieldNames(i), v.value)
          }
          i += 1
        }

        if (error != null)
          Left(error)
        else
          Right(
            DynamoValue(
              software.amazon.awssdk.services.dynamodb.model.AttributeValue
                .fromM(map)
            )
          )
      }
    }
  }
}

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
import cats.free.FreeApplicative
import cats.data.{Chain, Kleisli}
import scodec.bits.ByteVector

import Schema.ReadError
import Schema.structure._

object decoding {
  def fromSchema[A](s: Schema[A]): DynamoValue => Either[ReadError, A] = {
    println(s"building $s - decoder")
    s match {
      case Identity => _.asRight
      case Num => decodeNum
      case Str => decodeString
      case Bool => decodeBool
      case Bytes => decodeBytes
      case BytesSet => decodeBytesSet
      case NumSet => decodeNumSet
      case StrSet => decodeStrSet
      case Nul => decodeNull
      case Sequence(elem) => decodeSequence(elem, _)
      case Dictionary(elem) => decodeDictionary(elem, _)
      case Record(rec) =>
        // val here caches the traversal of the record
        val cachedDecoder = decodeRecord(rec)
        _.m.toRight(ReadError()).flatMap(cachedDecoder)
      case Sum(cases) => decodeSum(cases)
      case Isos(iso) => decodeIsos(iso, _)
      case Defer(schema) => schema().read
    }
  }

  type Res[A] = Either[ReadError, A]

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
      .flatMap(_.traverse(schema.read))

  def decodeDictionary[V](
      schema: Schema[V],
      value: DynamoValue
  ): Res[Map[String, V]] =
    value.m
      .toRight(ReadError())
      .flatMap(
        _.map { case (k, v) => k -> v }
          .traverse(schema.read)
      )

  def decodeRecord[R](
      recordSchema: FreeApplicative[Field[R, *], R]
  ): Map[String, DynamoValue] => Res[R] = {
    println("compiling records - decoder")

    type Target[A] =
      Kleisli[Either[ReadError, *], Map[String, DynamoValue], A]

    recordSchema.foldMap {
      new (Field[R, *] ~> Target) {
        def apply[A](field: Field[R, A]) = {
          println("traversing record structure - decoder")
          field match {
            case Field.Required(name, elemSchema, _) =>
              Kleisli { (v: Map[String, DynamoValue]) =>
                v.get(name)
                  .toRight(ReadError())
                  .flatMap(v => elemSchema.read(v))
              }
            case Field.Optional(name, elemSchema, _) =>
              Kleisli { (v: Map[String, DynamoValue]) =>
                v
                  .get(name)
                  .traverse(v => elemSchema.read(v))
              }
          }
        }
      }
    }.run
  }

  def decodeSum[A](cases: Chain[Alt[A]]): DynamoValue => Res[A] = {
    println("compiling sums - decoder")

    implicit def orElse[A]: Monoid[Option[A]] =
      MonoidK[Option].algebra

    cases
      .foldMap { alt =>
        println("traversing sum structure - decoder")
        (v: DynamoValue) =>
          alt.caseSchema.read(v).map(alt.prism.inject).toOption
      }
      .andThen(_.toRight(ReadError()))
  }

  def decodeIsos[V](xmap: XMap[V], v: DynamoValue): Res[V] =
    xmap.schema
      .read(v)
      .flatMap(xmap.r)

}

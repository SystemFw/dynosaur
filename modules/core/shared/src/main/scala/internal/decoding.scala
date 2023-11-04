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

import cats.{~>, Monoid}
import cats.syntax.all._
import alleycats.std.map._
import cats.free.FreeApplicative
import cats.data.{Chain, Kleisli}
import scodec.bits.ByteVector

import Schema.ReadError
import Schema.structure._
import scala.annotation.tailrec
import scala.collection.mutable.Builder

object decoding {
  def fromSchema[A](s: Schema[A]): DynamoValue => Either[ReadError, A] =
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
      case Record(rec) => { value =>
        // val here caches the traversal of the record
        val cachedDecoder = decodeRecord(rec)
        value.m
          .toRight(ReadError(s"value ${value.toString()} is not a Dictionary"))
          .flatMap(cachedDecoder)
      }
      case Sum(cases) => decodeSum(cases)
      case Isos(iso) => decodeIsos(iso, _)
      case Defer(schema) => schema().read
    }

  type Res[A] = Either[ReadError, A]

  def decodeBool: DynamoValue => Res[Boolean] = { value =>
    value.bool.toRight(ReadError(s"value ${value.toString()} is not a Boolean"))
  }

  def decodeNum: DynamoValue => Res[DynamoValue.Number] = { value =>
    value.n.toRight(ReadError(s"value ${value.toString()} is not a Number"))
  }

  def decodeString: DynamoValue => Res[String] = { value =>
    value.s.toRight(ReadError(s"value ${value.toString()} is not a String"))
  }

  def decodeBytes: DynamoValue => Res[ByteVector] = { value =>
    value.b.toRight(ReadError(s"value ${value.toString()} is not a ByteVector"))
  }

  def decodeBytesSet: DynamoValue => Res[NonEmptySet[ByteVector]] = { value =>
    value.bs.toRight(
      ReadError(s"value ${value.toString()} is not a ByteVector Set")
    )
  }

  def decodeNumSet: DynamoValue => Res[NonEmptySet[DynamoValue.Number]] = {
    value =>
      value.ns.toRight(
        ReadError(s"value ${value.toString()} is not a Number Set")
      )
  }

  def decodeStrSet: DynamoValue => Res[NonEmptySet[String]] = { value =>
    value.ss.toRight(
      ReadError(s"value ${value.toString()} is not a String Set")
    )
  }

  def decodeNull: DynamoValue => Res[Unit] = { value =>
    value.nul.toRight(
      ReadError(s"value ${value.toString()} is not a Null")
    )
  }

  def decodeSequence[V](
      schema: Schema[V],
      value: DynamoValue
  ): Res[List[V]] = {
    value.l match {
      case None =>
        Left(ReadError(s"value ${value.toString()} is not a Sequence"))
      case Some(xs) =>
        val lb = List.newBuilder[V]

        @tailrec
        def loop(
            xs: List[DynamoValue],
            result: Builder[V, List[V]]
        ): Either[ReadError, Builder[V, List[V]]] = xs match {
          case head :: next =>
            schema.read(head) match {
              case Right(value) =>
                loop(next, result.addOne(value))
              case Left(error) =>
                Left(error)
            }
          case Nil =>
            Right(result)
        }

        loop(xs, lb).map(_.result())
    }
  }

  def decodeDictionary[V](
      schema: Schema[V],
      value: DynamoValue
  ): Res[Map[String, V]] =
    value.m
      .toRight(ReadError(s"value ${value.toString()} is not a Dictionary"))
      .flatMap(
        _.map { case (k, v) => k -> v }
          .traverse(schema.read)
      )

  def decodeRecord[R](
      recordSchema: FreeApplicative[Field[R, *], R]
  ): Map[String, DynamoValue] => Res[R] = {

    type Target[A] =
      Kleisli[Either[ReadError, *], Map[String, DynamoValue], A]

    recordSchema.foldMap {
      new (Field[R, *] ~> Target) {
        def apply[A](field: Field[R, A]) =
          field match {
            case Field.Required(name, elemSchema, _) =>
              Kleisli { (v: Map[String, DynamoValue]) =>
                v.get(name)
                  .toRight(
                    ReadError(s"required field $name does not contain a value")
                  )
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
    }.run
  }

  def decodeSum[A](cases: Chain[Alt[A]]): DynamoValue => Res[A] = {

    type Decode = DynamoValue => Either[List[ReadError], A]

    val baseDecode: Decode = (v: DynamoValue) =>
      Either.left[List[ReadError], A](List.empty[ReadError])

    cases
      .foldLeft[Decode](baseDecode) { (acc, alt) =>
        acc.flatMap {
          case Left(value) =>
            (v) =>
              alt.caseSchema.read(v).map(alt.prism.inject).leftMap(e => List(e))
          case ok @ Right(value) => _ => ok
        }
      }
      .andThen { res =>
        res.leftMap { errors =>
          val errorsDetails = errors.map(_.message).mkString("[", ",", "]")
          ReadError(
            s"value doesn't match any of the alternatives: $errorsDetails"
          )
        }
      }
  }

  def decodeIsos[V](xmap: XMap[V], v: DynamoValue): Res[V] =
    xmap.schema
      .read(v)
      .flatMap(xmap.r)

}

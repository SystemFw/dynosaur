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

import cats.Id
import cats.implicits._
import cats.free.Free
import cats.data.Chain

import scodec.bits.ByteVector
import scala.collection.immutable

@annotation.implicitNotFound(
  """
Cannot find an implicit value for Schema[${A}].
 If you are trying to resolve a Schema for Option, note that there are no implicit instances for it because of the difference between missing fields, which can be absent from a record but cannot be NULL, and nullable fields, which can be NULL but have to be present.
Please use `field.opt(_, _)` or `field.apply(_, _)(Schema.nullable)` to tell `dynosaur` which semantics you want.
"""
)
sealed trait Schema[A] { self =>
  import Schema._
  import structure._

  override def toString = "Schema(..)"

  def tag(name: String): Schema[A] = record { field =>
    field(name, x => x)(this)
  }

  def xmap[B](
      f: A => Either[ReadError, B]
  )(g: B => Either[WriteError, A]): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        def schema = self
        def r = f
        def w = g
      }
    }

  def imap[B](f: A => B)(g: B => A): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        def schema = self
        def r = f.map(_.asRight)
        def w = g.map(_.asRight)
      }
    }

  def imapErr[B](f: A => Either[ReadError, B])(g: B => A): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        def schema = self
        def r = f
        def w = g.map(_.asRight)
      }
    }

  def nullable: Schema[Option[A]] =
    Schema.oneOf[Option[A]] { alt =>
      alt(this.imap(Some.apply)(_.value)) |+|
        alt(`null`.imap(_ => None)(_ => ()))
    }

  def asVector: Schema[Vector[A]] = Sequence(this)
  def asList: Schema[List[A]] = vector(this).imap(_.toList)(_.toVector)
  def asSeq: Schema[immutable.Seq[A]] =
    vector(this).imap(_.toSeq: Seq[A])(_.toVector)

  def asMap: Schema[Map[String, A]] = Dictionary(this)
}

object Schema {
  object structure {
    case object Identity extends Schema[AttributeValue]
    case object Num
        extends Schema[String] // dynamo represents numbers as strings
    case object Str extends Schema[String]
    case object Bool extends Schema[Boolean]
    case object Bytes extends Schema[ByteVector]
    case object NULL extends Schema[Unit]
    case object BytesSet extends Schema[NonEmptySet[ByteVector]]
    case object NumSet extends Schema[NonEmptySet[String]]
    case object StrSet extends Schema[NonEmptySet[String]]
    case class Dictionary[A](value: Schema[A]) extends Schema[Map[String, A]]
    case class Sequence[A](value: Schema[A]) extends Schema[Vector[A]]
    case class Record[R](value: Free[Field[R, *], R]) extends Schema[R]
    case class Sum[A](value: Chain[Alt[A]]) extends Schema[A]
    case class Isos[A](value: XMap[A]) extends Schema[A]

    trait Field[R, E]
    object Field {
      case class Required[R, E](
          name: String,
          elemSchema: Schema[E],
          get: R => E
      ) extends Field[R, E]

      case class Optional[R, E](
          name: String,
          elemSchema: Schema[E],
          get: R => Option[E]
      ) extends Field[R, Option[E]]
    }

    trait Alt[A] {
      type Case
      def caseSchema: Schema[Case]
      def prism: Prism[A, Case]
    }

    trait XMap[A] {
      type Repr
      def schema: Schema[Repr]
      def w: A => Either[WriteError, Repr]
      def r: Repr => Either[ReadError, A]
    }
  }

  import structure._

  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  implicit def id: Schema[AttributeValue] = Identity
  implicit def boolean: Schema[Boolean] = Bool
  implicit def string: Schema[String] = Str

  private def num[A: Numeric](convert: String => A): Schema[A] =
    Num.imapErr { v =>
      Either.catchNonFatal(convert(v)).leftMap(_ => ReadError())
    }(_.toString)

  private def numSet[A: Numeric](
      convert: String => A
  ): Schema[NonEmptySet[A]] = {
    import alleycats.std.set._

    NumSet.imapErr { nes =>
      nes.unsafeWithSet {
        _.traverse { v =>
          Either.catchNonFatal(convert(v)).leftMap(_ => ReadError())
        }
      }
    }(nes => nes.unsafeWithSet(_.map(_.toString).pure[Id]))
  }

  /*
   * Note:
   * No instance of Schema[Byte] bytes to avoid ambiguity between e.g
   * Vector[Byte] and dynamo BinarySet
   */

  implicit def int: Schema[Int] = num(_.toInt)
  implicit def long: Schema[Long] = num(_.toLong)
  implicit def double: Schema[Double] = num(_.toDouble)
  implicit def float: Schema[Float] = num(_.toFloat)
  implicit def short: Schema[Short] = num(_.toShort)

  implicit def byteVector: Schema[ByteVector] = Bytes
  implicit def byteArray: Schema[Array[Byte]] =
    Bytes.imap(_.toArray)(ByteVector.apply)

  implicit def intSet: Schema[NonEmptySet[Int]] = numSet(_.toInt)
  implicit def longSet: Schema[NonEmptySet[Long]] = numSet(_.toLong)
  implicit def doubleSet: Schema[NonEmptySet[Double]] = numSet(_.toDouble)
  implicit def floatSet: Schema[NonEmptySet[Float]] = numSet(_.toFloat)
  implicit def shortSet: Schema[NonEmptySet[Short]] = numSet(_.toShort)

  implicit def stringSet: Schema[NonEmptySet[String]] = StrSet

  implicit def byteVectorset: Schema[NonEmptySet[ByteVector]] = BytesSet
  implicit def byteArraySet: Schema[NonEmptySet[Array[Byte]]] =
    BytesSet.imap { nes =>
      nes.unsafeWithSet(_.map(_.toArray).pure[Id])
    } { nes =>
      nes.unsafeWithSet(_.map(ByteVector.apply).pure[Id])
    }

  // Seq is not enough on its own for implicit search to work
  implicit def vector[A](implicit s: Schema[A]): Schema[Vector[A]] = s.asVector
  implicit def list[A](implicit s: Schema[A]): Schema[List[A]] = s.asList
  implicit def seq[A](implicit s: Schema[A]): Schema[immutable.Seq[A]] = s.asSeq

  implicit def dict[A](implicit s: Schema[A]): Schema[Map[String, A]] = s.asMap

  def `null`: Schema[Unit] = NULL

  def nullable[A](implicit s: Schema[A]): Schema[Option[A]] = s.nullable

  def fields[R](p: Free[Field[R, *], R]): Schema[R] = Record(p)
  def record[R](b: FieldBuilder[R] => Free[Field[R, *], R]): Schema[R] =
    fields(b(field))

  def alternatives[A](cases: Chain[Alt[A]]): Schema[A] =
    Sum(cases)
  def oneOf[A](b: AltBuilder[A] => Chain[Alt[A]]): Schema[A] =
    alternatives(b(alt))

  def field[R] = new FieldBuilder[R]
  def alt[A] = new AltBuilder[A]

  class FieldBuilder[R] {
    def apply[E](
        name: String,
        get: R => E
    )(implicit elemSchema: Schema[E]): Free[Field[R, *], E] =
      Free.liftF(Field.Required(name, elemSchema, get))

    def pure[A](a: A): Free[Field[R, *], A] = Free.pure(a)

    def const[V](
        name: String,
        value: V
    )(implicit valueSchema: Schema[V]): Free[Field[R, *], Unit] =
      apply(name, _ => ()) {
        valueSchema.xmap { r =>
          Either.cond((r == value), (), ReadError())
        }(_ => value.asRight)
      }

    def opt[E](
        name: String,
        get: R => Option[E]
    )(implicit elemSchema: Schema[E]): Free[Field[R, *], Option[E]] =
      Free.liftF(Field.Optional(name, elemSchema, get): Field[R, Option[E]])
  }

  class AltBuilder[A] {
    def apply[B](
        caseSchema_ : Schema[B]
    )(implicit prism_ : Prism[A, B]): Chain[Alt[A]] =
      Chain.one {
        new Alt[A] {
          type Case = B
          def caseSchema = caseSchema_
          def prism = prism_
        }
      }
  }
}

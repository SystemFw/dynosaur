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

import cats.syntax.all._
import cats.Eval
import cats.free.FreeApplicative
import cats.data.Chain

import scodec.bits.ByteVector
import scala.collection.immutable

import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@annotation.implicitNotFound(
  """
Cannot find an implicit value for Schema[${A}].
 If you are trying to resolve a Schema for Option, note that there are no implicit instances for it because of the difference between missing fields, which can be absent from a record but cannot be Nul, and nullable fields, which can be NULL but have to be present.
Please use `field.opt(_, _)` or `field.apply(_, _)(Schema.nullable)` to tell `dynosaur` which semantics you want.
"""
)
sealed trait Schema[A] { self =>
  import Schema._
  import structure._

  def read(v: DynamoValue): Either[ReadError, A] =
    read_.value(v)

  def write(a: A): Either[WriteError, DynamoValue] =
    write_.value(a)

  def tag(name: String): Schema[A] = record { field =>
    field(name, x => x)(this)
  }

  def xmap[B](
      f: A => Either[ReadError, B]
  )(g: B => Either[WriteError, A]): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        val schema = self
        val r = f
        val w = g
      }
    }

  def imap[B](f: A => B)(g: B => A): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        val schema = self
        val r = f.map(_.asRight)
        val w = g.map(_.asRight)
      }
    }

  def imapErr[B](f: A => Either[ReadError, B])(g: B => A): Schema[B] =
    Isos {
      new XMap[B] {
        type Repr = A
        val schema = self
        val r = f
        val w = g.map(_.asRight)
      }
    }

  def nullable: Schema[Option[A]] =
    Schema.oneOf[Option[A]] { alt =>
      alt(this.imap(Some.apply)(_.value)) |+|
        alt(nul.imap(_ => None)(_ => ()))
    }

  def asList: Schema[List[A]] = Sequence(this)
  def asVector: Schema[Vector[A]] = list(this).imap(_.toVector)(_.toList)
  def asSeq: Schema[immutable.Seq[A]] =
    list(this).imap(_.toSeq: immutable.Seq[A])(_.toList)

  def asMap: Schema[Map[String, A]] = Dictionary(this)

  private val read_ : Eval[DynamoValue => Either[ReadError, A]] =
    Eval.later(internal.decoding.fromSchema(this))

  private val write_ : Eval[A => Either[WriteError, DynamoValue]] =
    Eval.later(internal.encoding.fromSchema(this))
}
object Schema {
  import structure._

  trait DynosaurError extends Exception with Product with Serializable {
    def message: String
  }
  case class ReadError(message: String) extends DynosaurError {
    override def toString(): String = s"ReadError($message)"
  }
  case class WriteError(message: String) extends DynosaurError {
    override def toString(): String = s"WriteError($message)"
  }

  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  implicit def id: Schema[DynamoValue] = Identity
  implicit def boolean: Schema[Boolean] = Bool
  implicit def string: Schema[String] = Str

  /*
   * Note:
   * No instance of Schema[Byte] bytes to avoid ambiguity between e.g
   * List[Byte] and dynamo BinarySet
   */
  implicit val int: Schema[Int] = num(_.toInt)
  implicit val long: Schema[Long] = num(_.toLong)
  implicit val double: Schema[Double] = num(_.toDouble)
  implicit val float: Schema[Float] = num(_.toFloat)
  implicit val short: Schema[Short] = num(_.toShort)

  implicit val byteVector: Schema[ByteVector] = Bytes
  implicit val byteArray: Schema[Array[Byte]] =
    Bytes.imap(_.toArray)(ByteVector.apply)

  implicit val intSet: Schema[NonEmptySet[Int]] = numSet(_.toInt)
  implicit val longSet: Schema[NonEmptySet[Long]] = numSet(_.toLong)
  implicit val doubleSet: Schema[NonEmptySet[Double]] = numSet(_.toDouble)
  implicit val floatSet: Schema[NonEmptySet[Float]] = numSet(_.toFloat)
  implicit val shortSet: Schema[NonEmptySet[Short]] = numSet(_.toShort)

  implicit val stringSet: Schema[NonEmptySet[String]] = StrSet

  implicit val byteVectorset: Schema[NonEmptySet[ByteVector]] = BytesSet
  implicit val byteArraySet: Schema[NonEmptySet[Array[Byte]]] =
    BytesSet.imap { nes =>
      NonEmptySet.unsafeFromSet(nes.value.map(_.toArray))
    } { nes =>
      NonEmptySet.unsafeFromSet(nes.value.map(ByteVector.apply))
    }

  // Seq is not enough on its own for implicit search to work
  implicit def vector[A](implicit s: Schema[A]): Schema[Vector[A]] = s.asVector
  implicit def list[A](implicit s: Schema[A]): Schema[List[A]] = s.asList
  implicit def seq[A](implicit s: Schema[A]): Schema[immutable.Seq[A]] = s.asSeq

  implicit def dict[A](implicit s: Schema[A]): Schema[Map[String, A]] = s.asMap

  implicit val nul: Schema[Unit] = Nul
  implicit val attributeValue: Schema[AttributeValue] =
    id.imap(_.value)(DynamoValue.apply)

  def defer[A](schema: => Schema[A]): Schema[A] = Defer(() => schema)

  def nullable[A](implicit s: Schema[A]): Schema[Option[A]] = s.nullable

  def fields[R](p: FreeApplicative[Field[R, *], R]): Schema[R] = Record(p)
  def record[R](
      b: FieldBuilder[R] => FreeApplicative[Field[R, *], R]
  ): Schema[R] =
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
    )(implicit elemSchema: Schema[E]): FreeApplicative[Field[R, *], E] =
      FreeApplicative.lift(Field.Required(name, elemSchema, get))

    def pure[A](a: A): FreeApplicative[Field[R, *], A] = FreeApplicative.pure(a)

    def const[V](
        name: String,
        value: V
    )(implicit valueSchema: Schema[V]): FreeApplicative[Field[R, *], Unit] =
      apply(name, _ => ()) {
        valueSchema.xmap { r =>
          Either.cond(
            (r == value),
            (),
            ReadError(
              s"${r.toString()} does not match expected const value of ${value.toString()}"
            )
          )
        }(_ => value.asRight)
      }

    def opt[E](
        name: String,
        get: R => Option[E]
    )(implicit elemSchema: Schema[E]): FreeApplicative[Field[R, *], Option[E]] =
      FreeApplicative.lift(
        Field.Optional(name, elemSchema, get): Field[R, Option[E]]
      )
  }

  class AltBuilder[A] {
    def apply[B](
        caseSchema_ : Schema[B]
    )(implicit prism_ : Prism[A, B]): Chain[Alt[A]] =
      Chain.one {
        new Alt[A] {
          type Case = B
          val caseSchema = caseSchema_
          val prism = prism_
        }
      }
  }

  object structure {
    case object Identity extends Schema[DynamoValue]
    case object Num extends Schema[DynamoValue.Number]
    case object Str extends Schema[String]
    case object Bool extends Schema[Boolean]
    case object Bytes extends Schema[ByteVector]
    case object Nul extends Schema[Unit]
    case object BytesSet extends Schema[NonEmptySet[ByteVector]]
    case object NumSet extends Schema[NonEmptySet[DynamoValue.Number]]
    case object StrSet extends Schema[NonEmptySet[String]]
    case class Dictionary[A](value: Schema[A]) extends Schema[Map[String, A]]
    case class Sequence[A](value: Schema[A]) extends Schema[List[A]]
    case class Record[R](value: FreeApplicative[Field[R, *], R])
        extends Schema[R]
    case class Sum[A](value: Chain[Alt[A]]) extends Schema[A]
    case class Isos[A](value: XMap[A]) extends Schema[A]
    case class Defer[A](value: () => Schema[A]) extends Schema[A]

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

  // TODO use parseFromString from Numeric, 2.13+
  private def num[A: Numeric](convert: String => A): Schema[A] =
    Num.imapErr { v =>
      Either
        .catchNonFatal(convert(v.value))
        .leftMap(_ => ReadError(s"Unable to parse ${v.toString()} as number"))
    }(n => DynamoValue.Number.of(n))

  private def numSet[A: Numeric](
      convert: String => A
  ): Schema[NonEmptySet[A]] = {
    import alleycats.std.set._

    NumSet.imapErr { nes =>
      nes.value
        .traverse { v =>
          Either
            .catchNonFatal(convert(v.value))
            .leftMap(_ =>
              ReadError(s"Unable to parse ${v.toString()} as number")
            )
        }
        .map(NonEmptySet.unsafeFromSet)
    }(nes =>
      NonEmptySet.unsafeFromSet(
        nes.value.map(n => DynamoValue.Number.of(n))
      )
    )
  }
}

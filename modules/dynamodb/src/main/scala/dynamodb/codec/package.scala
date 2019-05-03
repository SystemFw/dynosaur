/*
 * Copyright 2018 OVO Energy
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

import com.ovoenergy.comms.aws.dynamodb.model._

import cats._, implicits._
import cats.free.FreeApplicative

object SchemaEx {
  type Ap[F[_], A] = FreeApplicative[F, A]
  val Ap = FreeApplicative

  sealed trait Schema[A]
  case object Num extends Schema[Int]
  case object Str extends Schema[String]
  case class Rec[R](p: Ap[Field[R, ?], R]) extends Schema[R]

  case class Field[R, E](name: String, elemSchema: Schema[E], get: R => E)

  def field[R, E](
      name: String,
      elemSchema: Schema[E],
      get: R => E): Ap[Field[R, ?], E] =
    Ap.lift(Field(name, elemSchema, get))

  trait Encoder[A] {
    def write(a: A): AttributeValue
  }
  object Encoder {
    def instance[A](f: A => AttributeValue): Encoder[A] = new Encoder[A] {
      def write(a: A): AttributeValue = f(a)
    }

    def fromSchema[A](s: Schema[A]): Encoder[A] = {
      def encodeInt: Int => AttributeValue = AttributeValue.n(_)
      def encodeString: String => AttributeValue = AttributeValue.s(_)
      def encodeObject[R](record: Ap[Field[R, ?], R], v: R): AttributeValue.M =
        record.analyze {
          λ[Field[R, ?] ~> λ[a => AttributeValue.M]] { field =>
            AttributeValue.M(
              Map(AttributeName(field.name) -> fromSchema(field.elemSchema)
                .write(field.get(v))))
          }
        }

      s match {
        case Num => Encoder.instance(encodeInt)
        case Str => Encoder.instance(encodeString)
        case Rec(rec) =>
          Encoder.instance(v => encodeObject(rec, v): AttributeValue)
      }
    }
  }

  case class ParseError() extends Exception
  trait Decoder[A] {
    def read(v: AttributeValue): Either[ParseError, A]
  }
  object Decoder {
    def instance[A](f: AttributeValue => Either[ParseError, A]): Decoder[A] =
      new Decoder[A] {
        def read(v: AttributeValue): Either[ParseError, A] = f(v)
      }

    def fromSchema[A](s: Schema[A]): Decoder[A] = {
      type Res[B] = Either[ParseError, B]

      def decodeInt: AttributeValue => Res[Int] =
        _.n.toRight(ParseError()).flatMap { v =>
          Either.catchNonFatal(v.value.toInt).leftMap(_ => ParseError())
        }

      def decodeString: AttributeValue => Res[String] =
        _.s.toRight(ParseError()).map(_.value)

      def decodeObject[R](
          record: Ap[Field[R, ?], R],
          v: AttributeValue.M): Res[R] =
        record.foldMap {
          λ[Field[R, ?] ~> Res] { field =>
            v.values
              .get(AttributeName(field.name))
              .toRight(ParseError())
              .flatMap { v =>
                fromSchema(field.elemSchema).read(v)
              }
          }
        }

      s match {
        case Num => Decoder.instance(decodeInt)
        case Str => Decoder.instance(decodeString)
        case Rec(rec) =>
          Decoder.instance {
            _.m.toRight(ParseError()).flatMap(decodeObject(rec, _))
          }
      }
    }
  }

  case class User(id: Int, name: String)
  case class Role(capability: String, u: User)

  def s: Schema[User] =
    Rec(
      (
        field("id", Num, (_: User).id),
        field("name", Str, (_: User).name)
      ).mapN(User.apply)
    )

  def s2: Schema[Role] = Rec(
    (
      field("capability", Str, (_: Role).capability),
      field("user", s, (_: Role).u)).mapN(Role.apply)
  )

  def u = User(20, "joe")
  def role = Role("admin", u)

  def e = Encoder.fromSchema(s).write(u)
//   scala> SchemaEx.r
// res1: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))

  def e2 = Encoder.fromSchema(s2).write(role)
//   scala> SchemaEx.r2
// res2: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(capability) -> S(admin), AttributeName(user) -> M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))))
  // }

  import Ap._
  def a =
    (
      field("id", Num, (_: User).id),
      field("name", Str, (_: User).name)
    ).mapN(User.apply)

  def b =
    (
      field("capability", Str, (_: Role).capability),
      field("user", s, (_: Role).u)
    ).mapN(Role.apply)

  def c =
    (
      field("capability", Str, (_: Role).capability),
      field("user", s, (_: Role).u)
    ).tupled.map((Role.apply _).tupled)

  def d = (lift(1.some), lift(5.some), lift(6.some)).tupled

//   scala> b.show == c.show
// res2: Boolean = false

// scala> b.normalise.show == c.normalise.show
// res3: Boolean = true

}

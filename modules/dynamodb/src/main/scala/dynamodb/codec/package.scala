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

object SchemaEx {
  sealed trait Schema[A]
  case object Num extends Schema[Int]
  case object Str extends Schema[String]
  case class Rec[R](p: FreeAp[Props[R, ?], R]) extends Schema[R]

  case class Props[R, E](name: String, elemSchema: Schema[E], get: R => E)

  def props[R, E](
      name: String,
      elemSchema: Schema[E],
      get: R => E): FreeAp[Props[R, ?], E] =
    FreeAp.lift(Props(name, elemSchema, get))

  trait Encoder[A] {
    def apply(a: A): AttributeValue
  }
  object Encoder {
    def instance[A](f: A => AttributeValue): Encoder[A] = new Encoder[A] {
      def apply(a: A): AttributeValue = f(a)
    }

    def fromSchema[A](s: Schema[A]): Encoder[A] = {
      def encodeInt: Int => AttributeValue = AttributeValue.n(_)
      def encodeString: String => AttributeValue = AttributeValue.s(_)
      def encodeObject[R](ap: FreeAp[Props[R, ?], R], v: R): AttributeValue.M =
        ap.analyze {
          λ[Props[R, ?] ~> λ[a => AttributeValue.M]] { p =>
            AttributeValue.M(
              Map(AttributeName(p.name) -> fromSchema(p.elemSchema)(p.get(v))))
          }
        }

      s match {
        case Num => Encoder.instance(encodeInt)
        case Str => Encoder.instance(encodeString)
        case Rec(p) => Encoder.instance(v => encodeObject(p, v): AttributeValue)
      }

    }
  }

  case class User(id: Int, name: String)
  case class Role(capability: String, u: User)

  def s: Schema[User] =
    Rec(
      (
        props("id", Num, (_: User).id),
        props("name", Str, (_: User).name)
      ).mapN(User.apply)
    )

  def s2: Schema[Role] = Rec(
    (
      props("capability", Str, (_: Role).capability),
      props("user", s, (_: Role).u)).mapN(Role.apply)
  )

  def u = User(20, "joe")
  def role = Role("admin", u)

  def r = Encoder.fromSchema(s)(u)
//   scala> SchemaEx.r
// res1: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))

  def r2 = Encoder.fromSchema(s2)(role)
//   scala> SchemaEx.r2
// res2: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(capability) -> S(admin), AttributeName(user) -> M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))))
  // }

  import FreeAp._
  def a =
    (
      props("id", Num, (_: User).id),
      props("name", Str, (_: User).name)
    ).mapN(User.apply)

  def b =
    (
      props("capability", Str, (_: Role).capability),
      props("user", s, (_: Role).u)
    ).mapN(Role.apply)

  def c =
    (
      props("capability", Str, (_: Role).capability),
      props("user", s, (_: Role).u)
    ).tupled.map((Role.apply _).tupled)

  def d = (lift(1.some), lift(5.some), lift(6.some)).tupled

//   scala> b.show == c.show
// res2: Boolean = false

// scala> b.normalise.show == c.normalise.show
// res3: Boolean = true

}

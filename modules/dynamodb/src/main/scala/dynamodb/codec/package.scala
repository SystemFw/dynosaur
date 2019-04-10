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

import cats._, data._, implicits._, free._

object SchemaEx {
  import io.circe._

  type FreeAp[F[_], A] = FreeApplicative[F, A]

  sealed trait Schema[A]
  case object Num extends Schema[Int]
  case object Str extends Schema[String]
  case class Rec[R](p: FreeAp[Props[R, ?], R]) extends Schema[R]

  case class Props[R, E](name: String, elemSchema: Schema[E], get: R => E)

  def props[R, E](
      name: String,
      elemSchema: Schema[E],
      get: R => E): FreeAp[Props[R, ?], E] =
    FreeApplicative.lift(Props(name, elemSchema, get))

  case class User(id: Int, name: String)

  val s: Schema[User] =
    Rec(
      (
        props("id", Num, (_: User).id),
        props("name", Str, (_: User).name)
      ).mapN(User.apply)
    )

  val u = User(20, "joe")

  val r = Encoder.fromSchema(s)(u)
//   scala> SchemaEx.r
// res1: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))

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
          new (Props[R, ?] ~>(Lambda[a => AttributeValue.M])) {
            def apply[B](p: Props[R, B]): AttributeValue.M =
              AttributeValue.M(
                Map(AttributeName(p.name) -> fromSchema(p.elemSchema)
                  .apply(p.get(v))))
          }
        }

      s match {
        case Num => Encoder.instance(encodeInt)
        case Str => Encoder.instance(encodeString)
        case Rec(p) => Encoder.instance(v => encodeObject(p, v): AttributeValue)
      }

    }
  }

}

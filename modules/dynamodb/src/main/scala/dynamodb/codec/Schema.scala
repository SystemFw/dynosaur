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

import cats.free.FreeApplicative

sealed trait Schema[A]
object Schema {
  object structure {
    type Ap[F[_], A] = FreeApplicative[F, A]
    val Ap = FreeApplicative

    case object Num extends Schema[Int]
    case object Str extends Schema[String]
    case class Rec[R](p: Ap[Field[R, ?], R]) extends Schema[R]
    case class Sum[A](alt: List[Alt[A]]) extends Schema[A]

    case class Field[R, E](name: String, elemSchema: Schema[E], get: R => E)
    trait Alt[A] {
      type B
      def id: String
      def caseSchema: Schema[B]
      def review: B => A
      def preview: A => Option[B]
    }

  }

  import structure._

  def str: Schema[String] = Str
  def num: Schema[Int] = Num
  def rec[R](p: Ap[Field[R, ?], R]): Schema[R] = Rec(p)
  def oneOf[A](cases: List[Alt[A]]): Schema[A] =
    Sum(cases)

  def field[R] = new FieldBuilder[R]

  class FieldBuilder[R] {
    def apply[E](
        name: String,
        elemSchema: Schema[E],
        get: R => E): Ap[Field[R, ?], E] =
      Ap.lift(Field(name, elemSchema, get))
  }

  def alt[A] = new AltBuilder[A]

  class AltBuilder[A] {
    def apply[B_](id_ : String, caseSchema_ : Schema[B_])(review_ : B_ => A)(
        preview_ : PartialFunction[A, B_]): Alt[A] = new Alt[A] {
      type B = B_
      def id = id_
      def caseSchema = caseSchema_
      def review = review_
      def preview = preview_.lift
    }
  }
}

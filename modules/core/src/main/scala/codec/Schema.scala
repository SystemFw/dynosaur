/*
 * Copyright 2019 OVO Energy
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
package codec

import cats.implicits._
import cats.free.Free
import cats.data.Chain

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

  // TODO remove this and inline it into field const?
  def const[B](repr: A, v: B): Schema[B] =
    this.xmap { r =>
      (r == repr).guard[Option].toRight(ReadError()).as(v)
    } { _ =>
      repr.asRight
    }
}

object Schema {
  object structure {
    case object Num extends Schema[Int]
    case object Str extends Schema[String]
    case class Rec[R](p: Free[Field[R, ?], R]) extends Schema[R]
    case class Sum[A](alt: Chain[Alt[A]]) extends Schema[A]
    case class Isos[A](x: XMap[A]) extends Schema[A]

    case class Field[R, E](name: String, elemSchema: Schema[E], get: R => E)
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

  def str: Schema[String] = Str
  def num: Schema[Int] = Num

  def fields[R](p: Free[Field[R, ?], R]): Schema[R] = Rec(p)
  def record[R](b: FieldBuilder[R] => Free[Field[R, ?], R]): Schema[R] =
    fields(b(field))
  def emptyRecord: Schema[Unit] = record(_.pure(()))

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
    )(elemSchema: Schema[E]): Free[Field[R, ?], E] =
      Free.liftF(Field(name, elemSchema, get))

    def pure[A](a: A): Free[Field[R, ?], A] = Free.pure(a)

    def const[V](
        name: String,
        value: V
    )(valueSchema: Schema[V]): Free[Field[R, ?], Unit] =
      Free.liftF(Field(name, valueSchema.const(value, ()), _ => ()))
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

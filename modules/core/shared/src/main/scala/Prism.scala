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

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/** Selects a branch of a coproduct type.
  */
@implicitNotFound(
  "Cannot find an implicit Prism[${A}, ${B}]. Write an instance manually, or check whether ${B} is a subtype of ${A} if you want the library to provide one for you automatically."
)
case class Prism[A, B](tryGet: A => Option[B], inject: B => A)
object Prism extends LowPrioPrism {

  /** Returns a new [[Prism]] instance using the specified `tryGet` partial
    * function and `inject` function.
    */
  def fromPartial[A, B](tryGet: PartialFunction[A, B])(inject: B => A) =
    Prism(tryGet.lift, inject)

  /** Returns a new [[Prism]] for the specified type.
    */
  implicit def identity[A]: Prism[A, A] =
    Prism[A, A](Option.apply _, a => a)

  /** Returns a new [[Prism]] from `Either[A, B]` to `Left[A, B]`.
    */
  implicit def left[A, B]: Prism[Either[A, B], Left[A, B]] =
    Prism.fromPartial[Either[A, B], Left[A, B]] { case v @ Left(_) =>
      v
    }(v => v)

  /** Returns a new [[Prism]] from `Either[A, B]` to `Right[A, B]`.
    */
  implicit def right[A, B]: Prism[Either[A, B], Right[A, B]] =
    Prism.fromPartial[Either[A, B], Right[A, B]] { case v @ Right(_) =>
      v
    }(v => v)

  /** Returns a new [[Prism]] from `Option` to `None`.
    */
  implicit def none[A]: Prism[Option[A], None.type] =
    Prism.fromPartial[Option[A], None.type] { case v: None.type =>
      v
    }(none => none)

  /** Returns a new [[Prism]] from `Option` to `Some`.
    */
  implicit final def some[A]: Prism[Option[A], Some[A]] =
    Prism.fromPartial[Option[A], Some[A]] { case v @ Some(_) =>
      v
    }(v => v)
}

private[dynosaur] sealed trait LowPrioPrism {

  /** Returns a new [[Prism]] for the specified supertype and subtype.
    *
    * Relies on class tags. Since the function is implicit, [[Prism]]s are
    * available implicitly for any supertype and subtype relationships.
    */
  implicit final def derive[S, A <: S](implicit
      tag: ClassTag[A]
  ): Prism[S, A] = {
    val tryGet = (s: S) =>
      if (tag.runtimeClass.isInstance(s))
        Some(s.asInstanceOf[A])
      else None

    val inject = (a: A) => (a: S)

    Prism(tryGet, inject)
  }
}

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

/** Optic used for selecting a part of a coproduct type.
  */
@implicitNotFound(
  "could not find implicit Prism[${S}, ${A}]; ensure ${A} is a subtype of ${S} or manually define an instance"
)
sealed abstract class Prism[S, A] {

  /** Attempts to select a coproduct part. */
  def tryGet: S => Option[A]

  /** Creates a coproduct from a coproduct part. */
  def inject: A => S
}

object Prism extends PrismLowPriority {

  /** Returns the [[Prism]] for the specified types.
    */
  final def apply[S, A](implicit prism: Prism[S, A]): Prism[S, A] =
    prism

  /** Returns a new [[Prism]] for the specified type.
    */
  implicit final def identity[A]: Prism[A, A] =
    Prism.instance[A, A](Some(_))(a => a)

  /** Returns a new [[Prism]] instance using the specified
    * `tryGet` and `inject` functions.
    */
  final def instance[S, A](
      tryGet: S => Option[A]
  )(inject: A => S): Prism[S, A] = {
    val _tryGet = tryGet
    val _inject = inject

    new Prism[S, A] {
      override final val tryGet: S => Option[A] =
        _tryGet

      override final val inject: A => S =
        _inject

      override final def toString: String =
        "Prism$" + System.identityHashCode(this)
    }
  }

  /** Returns a new [[Prism]] from `Either[A, B]` to `Left[A, B]`.
    */
  implicit final def left[A, B]: Prism[Either[A, B], Left[A, B]] =
    Prism.instance[Either[A, B], Left[A, B]] {
      case left @ Left(_) => Some(left)
      case Right(_) => None
    }(left => left)

  /** Returns a new [[Prism]] from `Option` to `None`.
    */
  implicit final def none[A]: Prism[Option[A], None.type] =
    Prism.instance[Option[A], None.type] {
      case None => Some(None)
      case Some(_) => None
    }(none => none)

  /** Returns a new [[Prism]] instance using the specified
    * `get` partial function and `inject` function.
    */
  final def fromPartial[S, A](get: PartialFunction[S, A])(
      inject: A => S
  ): Prism[S, A] =
    Prism.instance(get.lift)(inject)

  /** Returns a new [[Prism]] from `Either[A, B]` to `Right[A, B]`.
    */
  implicit final def right[A, B]: Prism[Either[A, B], Right[A, B]] =
    Prism.instance[Either[A, B], Right[A, B]] {
      case Left(_) => None
      case right @ Right(_) => Some(right)
    }(right => right)

  /** Returns a new [[Prism]] from `Option` to `Some`.
    */
  implicit final def some[S, A](implicit
      prism: Prism[S, A]
  ): Prism[Option[S], Some[A]] =
    Prism.instance[Option[S], Some[A]] {
      case None => None
      case Some(s) => prism.tryGet(s).map(Some(_))
    }(_.map(prism.inject))
}

private[dynosaur] sealed abstract class PrismLowPriority {

  /** Returns a new [[Prism]] for the specified supertype
    * and subtype.
    *
    * Relies on class tags. Since the function is implicit,
    * [[Prism]]s are available implicitly for any supertype
    * and subtype relationships.
    */
  implicit final def derive[S, A <: S](implicit
      tag: ClassTag[A]
  ): Prism[S, A] = {
    val tryGet = (s: S) =>
      if (tag.runtimeClass.isInstance(s))
        Some(s.asInstanceOf[A])
      else None

    val inject = (a: A) => (a: S)

    Prism.instance(tryGet)(inject)
  }
}

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
package model

import cats._, implicits._

object NonEmptySetImpl {
  private[NonEmptySetImpl] type Base
  private[NonEmptySetImpl] trait Tag extends Any
  type Type[A] <: Base with Tag

  private[NonEmptySetImpl] def create[A](s: Set[A]): Type[A] =
    s.asInstanceOf[Type[A]]

  private[NonEmptySetImpl] def unwrap[A](s: Type[A]): Set[A] =
    s.asInstanceOf[Set[A]]

  def fromSet[A](as: Set[A]): Option[NonEmptySet[A]] =
    if (as.nonEmpty) create(as).some else none

  def fromNonEmptySet[A: Order](as: cats.data.NonEmptySet[A]) =
    create(as.toSortedSet.toSet)

  /** Note:
    * It's up to you to ensure the non empty invariant is preserved
    */
  def unsafeFromSet[A](set: Set[A]): NonEmptySet[A] =
    if (set.nonEmpty) create(set)
    else
      throw new IllegalArgumentException(
        "Cannot create NonEmptySet from empty set"
      )

  def apply[A](head: A, tail: Set[A] = Set.empty): NonEmptySet[A] =
    create(Set(head) ++ tail)

  sealed implicit class Ops[A](value: NonEmptySet[A]) {
    def contains(a: A): Boolean = value.toSet.contains(a)
    def toSet: Set[A] = unwrap(value)

    /** Note:
      * It's up to you to ensure the non empty invariant is preserved
      */
    def unsafeWithSet[F[_]: Functor, B](
        f: Set[A] => F[Set[B]]
    ): F[NonEmptySet[B]] =
      f(value.toSet).map(unsafeFromSet)

    def toNonEmptySet(implicit ev: Ordering[A]): cats.data.NonEmptySet[A] =
      cats.data.NonEmptySet
        .fromSetUnsafe(toSet.to(collection.immutable.SortedSet))
  }
}

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

/** A Set that cannot be empty, to support DynamoDb number, string and binary
  * sets.
  */
sealed abstract case class NonEmptySet[A](value: Set[A])
object NonEmptySet {
  def of[A](head: A, tail: A*): NonEmptySet[A] =
    create((head +: tail).toSet)

  def fromSet[A](set: Set[A]): Option[NonEmptySet[A]] =
    if (set.nonEmpty) create(set).some
    else none

  def fromCatsNonEmpty[A](set: cats.data.NonEmptySet[A]): NonEmptySet[A] =
    create(set.toSortedSet.toSet)

  /** Note: It's up to you to ensure the non empty invariant holds
    */
  def unsafeFromSet[A](set: Set[A]): NonEmptySet[A] =
    if (set.nonEmpty) create(set)
    else
      throw new IllegalArgumentException(
        "Cannot create NonEmptySet from empty set"
      )

  def toSet[A](v: Option[NonEmptySet[A]]): Set[A] =
    v.map(_.value).getOrElse(Set.empty)

  private def create[A](set: Set[A]) =
    new NonEmptySet(set) {}
}

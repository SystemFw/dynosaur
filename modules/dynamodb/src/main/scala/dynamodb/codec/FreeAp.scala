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

import cats._, data._, implicits._, arrow._

sealed trait FreeAp[F[_], A] {
  def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[A]

  final def analyze[M: Monoid](f: F ~> λ[α => M]): M =
    foldMap[Const[M, ?]](
      λ[F ~> Const[M, ?]](x => Const(f(x)))
    ).getConst

  def normalise = FreeAp.norm(this)

  def isOne: Boolean
  def isLift: Boolean
  def isZip: Boolean
  def show: String
}
object FreeAp {

  case class Lift[F[_], A](fa: F[A]) extends FreeAp[F, A] {
    def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[A] = f(fa)
    def isOne: Boolean = false
    def isLift: Boolean = true
    def isZip: Boolean = false

    def show = s"Lift(..)"
  }
  case class One[F[_]]() extends FreeAp[F, Unit] {
    def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[Unit] =
      ().pure[G]
    def isOne: Boolean = true
    def isLift: Boolean = false
    def isZip: Boolean = false

    def show = "One"
  }
  case class Zip[F[_], A, B](fa: FreeAp[F, A], fb: FreeAp[F, B])
      extends FreeAp[F, (A, B)] {
    def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[(A, B)] =
      fa.foldMap(f) product fb.foldMap(f)
    def isOne: Boolean = false
    def isLift: Boolean = false
    def isZip: Boolean = true
    def show = s"Zip(${fa.show},${fb.show})"
  }
  case class Map[F[_], A, B](fa: FreeAp[F, A], ff: A => B)
      extends FreeAp[F, B] {
    def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[B] =
      fa.foldMap(f).map(ff)
    def isOne: Boolean = false
    def isLift: Boolean = false
    def isZip: Boolean = false

    def show = s"Map(${fa.show}, λ)"
  }

  def lift[F[_], A](fa: F[A]): FreeAp[F, A] = Lift(fa)

  implicit final def freeApplicative[S[_]]: Applicative[FreeAp[S, ?]] =
    new Applicative[FreeAp[S, ?]] {
      def ap[A, B](f: FreeAp[S, A => B])(fa: FreeAp[S, A]): FreeAp[S, B] =
        Map(Zip(f, fa), (t: (A => B, A)) => t._1(t._2))

      def pure[A](a: A): FreeAp[S, A] = Map(One[S](), (_: Unit) => a)
    }

  /**
    * Transform any Free Applicative expression into the following normal form:
    * ```
    * Map(Zip(Lift(..),Zip(Lift(..),Lift(..))), λ)
    * ```
    * a Map node containing right-nested products of Lift nodes.
    * This affords easy navigation since you basically a have a List of Lifts.
    *
    * This is a modified version of the algorithm presented in
    * 'Lifting Operators and Laws' by Ralf Hinze, except the paper
    * associates to the left instead.
    *
    * The logic here is greatly obscured by all the hacks and
    * abominations required to work with GADTs in scala, look at
    * `freeAp.hs` for a sane Haskell version that justifies each
    * step.
    *
    */
  def norm[F[_], A]: FreeAp[F, A] => FreeAp[F, A] = e => {
    norm1(e) match {
      case Map(u, f) =>
        norm2(u) match {
          case Map(v, g) => Map(v, f compose g)
          case _ => throw new Exception("Guaranteed to return Map")
        }
      case _ => throw new Exception("Guaranteed to return Map")
    }
  }

  def norm1[F[_], A]: FreeAp[F, A] => FreeAp[F, A] = {
    case Lift(fa) => Map(Lift(fa), identity[A])
    case Map(e, f) =>
      norm1(e) match {
        case Map(u, g) => Map(u, f compose g)
        case _ => throw new Exception("Guaranteed to return Map")
      }
    case v: One[a] => Map(v, identity[A])
    case v: Zip[F, a, b] =>
      (norm1(v.fa), norm1(v.fb)) match {
        case (Map(fa, f), Map(ga, g)) =>
          Map(Zip(fa, ga), Arrow[Function1].split(f, g))
        case _ => throw new Exception("Guaranteed to return Map")
      }
  }

  def norm2[F[_], A]: FreeAp[F, A] => FreeAp[F, A] = {
    case Lift(fa) => Map(Lift(fa), identity[A])
    case v: One[a] => Map(v, identity[A])
    case z: Zip[F, a, b] if z.fa.isOne =>
      norm2(z.fb) match {
        case m: Map[F, c, d] =>
          Map(m.fa, (x: c) => ((), m.ff(x))).asInstanceOf[FreeAp[F, A]]
        case _ => throw new Exception("Guaranteed to return Map")
      }
    case z: Zip[F, a, b] if z.fb.isOne =>
      norm2(z.fa) match {
        case m: Map[F, c, d] =>
          Map(m.fa, (x: c) => (m.ff(x), ())).asInstanceOf[FreeAp[F, A]]
        case _ => throw new Exception("Guaranteed to return Map")
      }
    case z: Zip[F, a, b] if z.fa.isLift =>
      norm2(z.fb) match {
        case m: Map[F, c, d] =>
          Map(Zip(z.fa, m.fa), Arrow[Function1].split(identity[a], m.ff))
        case _ => throw new Exception("Guaranteed to return Map")
      }
    case z1: Zip[F, a, b] if z1.fa.isZip =>
      z1.fa match {
        case z2: Zip[F, c, d] =>
          norm2(Zip(z2.fa, Zip(z2.fb, z1.fb))) match {
            case m: Map[F, e, (a, (b, c))] =>
              def fun: e => ((a, b), c) = in => {
                val (x, (y, z)) = m.ff(in)
                ((x, y), z)
              }

              Map(m.fa, fun)
            case _ => throw new Exception("Guaranteed to return Map")
          }
        case _ => throw new Exception("Guaranteed to return Map")
      }
  }
}

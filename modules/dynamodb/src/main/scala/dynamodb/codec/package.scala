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

import cats._, data._, implicits._

object SchemaEx {
  sealed trait FreeAp[F[_], A] {
    final def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[A] = ???
    //   this match {
    //     case FreeAp.Lift(fa) => f(fa)
    //     case FreeAp.Pure(a) => a.pure[G]
    //     case FreeAp.Map2(fa, fb, fab) =>
    //       (fa.foldMap(f), fb.foldMap(f)).mapN(fab)
    //   }

    final def analyze[M: Monoid](f: F ~> λ[α => M]): M =
      foldMap[Const[M, ?]](
        λ[F ~> Const[M, ?]](x => Const(f(x)))
      ).getConst

    // def P = this match {
    //   case FreeAp.Pure(a) => a.some
    //   case _ => None
    // }

    // def L = this match {
    //   case FreeAp.Lift(a) => a.some
    //   case _ => None
    // }

    // def M = this match {
    //   case FreeAp.Map2(fa, fb, f) => (fa, fb, f).some
    //   case _ => None
    // }

  }
  object FreeAp {
    case class Lift[F[_], A](fa: F[A]) extends FreeAp[F, A]
    case class One[F[_]]() extends FreeAp[F, Unit]
    case class Zip[F[_], A, B](fa: FreeAp[F, A], fb: FreeAp[F, B])
        extends FreeAp[F, (A, B)]
    case class Map[F[_], A, B](fa: FreeAp[F, A], f: A => B) extends FreeAp[F, B]
    // case class Pure[F[_], A](a: A) extends FreeAp[F, A]
    // case class Ap[F[_], B, A](f: FreeAp[F, B => A], fa: FreeAp[F, B])
    //     extends FreeAp[F, A]
    // case class Map2[F[_], B, C, A](
    //     fa: FreeAp[F, B],
    //     fb: FreeAp[F, C],
    //     f: (B, C) => A)
    //     extends FreeAp[F, A]

    def lift[F[_], A](fa: F[A]): FreeAp[F, A] = Lift(fa)

    implicit final def freeApplicative[S[_]]: Applicative[FreeAp[S, ?]] =
      new Applicative[FreeAp[S, ?]] {
        def ap[A, B](f: FreeAp[S, A => B])(fa: FreeAp[S, A]): FreeAp[S, B] =
          ???

        def pure[A](a: A): FreeAp[S, A] = Map(One[S](), (_: Unit) => a)
      }
  }

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

  // def p[A](s: Schema[A]): String = s match {
  //   case Rec(p) =>
  //     import FreeAp._
  //     def t[F[_], A](f: FreeAp[F, A]): String =
  //       f match {
  //         case Pure(a) => "pure\n"
  //         case Lift(fa) => "lift\n"
  //         case Map2(fa, fb, _) => t(fa) ++ "map2" ++ t(fb)
  //       }
  //     t(p)
  // p match {
  //   case FreeAp.Map2(npp, fb, f) =>
  //     npp match {
  //       case FreeAp.Lift(Props(a, sch, get)) =>
  //         //FreeAp.Map2(FreeAp.Lift(Props("userId", sch, get)), fb, f)
  //         "target"
  //       case a =>
  //         s"first arg not lift but $a"
  //     }
  //   case _ =>
  //     "not map2"
  // }
  //   case _ => "not rec"
  // }

  def u = User(20, "joe")
  def role = Role("admin", u)

  def r = Encoder.fromSchema(s)(u)
//   scala> SchemaEx.r
// res1: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))

  def r2 = Encoder.fromSchema(s2)(role)
//   scala> SchemaEx.r2
// res2: com.ovoenergy.comms.aws.dynamodb.model.AttributeValue = M(Map(AttributeName(capability) -> S(admin), AttributeName(user) -> M(Map(AttributeName(id) -> N(20), AttributeName(name) -> S(joe)))))

//  def r3 = p(s) //Encoder.fromSchema(p(s))(u)

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
}

// res0: SchemaEx.Schema[SchemaEx.User] = Rec(
//   Map2(
//     Pure(cats.SemigroupalArityFunctions$$Lambda$8221/1365146284@d7c7bd7),
//     Map2(
//       Map2(
//         Pure(cats.Apply$$Lambda$8219/1793679587@50de6f7d),
//         Lift(Props(id,Num,SchemaEx$$$Lambda$8216/567770037@73be89dc)),
//         SchemaEx$FreeAp$$anon$2$$Lambda$8220/1257227434@7d66e64b
//       ),
//       Lift(Props(name,Str,SchemaEx$$$Lambda$8217/999950115@13aa88f3)),
//       SchemaEx$FreeAp$$anon$2$$Lambda$8220/1257227434@7d66e64b
//     ),
//     SchemaEx$FreeAp$$anon$2$$Lambda$8220/1257227434@7d66e64b
//   )
// )

// Rec(
//   Map2(
//     Pure(cats.SemigroupalArityFunctions$$Lambda$8242/1043824291@52f1d072),
//     Map2(
//       Map2(
//         Pure(cats.Apply$$Lambda$8240/104302346@68c047c),
//         Lift(Props(capability,Str,SchemaEx$$$Lambda$8243/613285585@530bfde2)),
//         SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36
//       ),
//       Lift(Props(user,Rec
//         (Map2(
//           Pure(cats.SemigroupalArityFunctions$$Lambda$8242/1043824291@28935feb),
//           Map2(
//             Map2(
//               Pure(cats.Apply$$Lambda$8240/104302346@68c047c),
//               Lift(Props(id,Num,SchemaEx$$$Lambda$8237/1254776692@57033e35)),
//               SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36
//             ),
//             Lift(Props(name,Str,SchemaEx$$$Lambda$8238/1148285031@4bbf0af6)),
//             SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36
//         ),
//           SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36)
//         ),SchemaEx$$$Lambda$8244/900307432@530f94bf)
//       ),SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36),
//     SchemaEx$FreeAp$$anon$2$$Lambda$8241/684715828@79617e36)
// )

// Rec(
//   Ap(
//     Pure(cats.SemigroupalArityFunctions$$Lambda$9990/123158537@55b9832a),
//     Ap(
//       Ap(
//         Pure(cats.Apply$$Lambda$9989/779740805@477b16c),
//         Lift(Props(id,Num,SchemaEx$$$Lambda$9986/1225130355@1d4ed21b))
//       ),
//       Lift(Props(name,Str,SchemaEx$$$Lambda$9987/449885094@4bc52312))
//     )
//   )
// )

// pure f <*> ap(ap(pure(g), lift("id")), lift("name")))
// res2: SchemaEx.Schema[SchemaEx.Role] = Rec(Ap(Pure(cats.SemigroupalArityFunctions$$Lambda$9990/123158537@6aa41ffa),Ap(Ap(Pure(cats.Apply$$Lambda$9989/779740805@477b16c),Lift(Props(capability,Str,SchemaEx$$$Lambda$10004/147226086@51b67605))),Lift(Props(user,Rec(Ap(Pure(cats.SemigroupalArityFunctions$$Lambda$9990/123158537@4817a59),Ap(Ap(Pure(cats.Apply$$Lambda$9989/779740805@477b16c),Lift(Props(id,Num,SchemaEx$$$Lambda$9986/1225130355@1d4ed21b))),Lift(Props(name,Str,SchemaEx$$$Lambda$9987/449885094@4bc52312))))),SchemaEx$$$Lambda$10005/1170347022@38cdec3f)))))

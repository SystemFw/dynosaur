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

//  def r3 = p(s) //Encoder.fromSchema(p(s))(u)

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

  sealed trait FreeAp[F[_], A] {
    def foldMap[G[_]](f: F ~> G)(implicit G: Applicative[G]): G[A]

    final def analyze[M: Monoid](f: F ~> λ[α => M]): M =
      foldMap[Const[M, ?]](
        λ[F ~> Const[M, ?]](x => Const(f(x)))
      ).getConst

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
  }

  import FreeAp._

  // can use something from arrow probably
  def x[A, C, B, D](f1: A => B, f2: C => D): ((A, C)) => (B, D) = {
    case (a, b) => f1(a) -> f2(b)
  }

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
          Map(Zip(fa, ga), x(f, g))
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
          Map(Zip(z.fa, m.fa), x(identity[a], m.ff))
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

// scala> norm(b).show == norm(c).show
// res3: Boolean = true

//   {-# Language GADTs #-}

// data FreeAp f a where
//   Lift :: f a -> FreeAp f a
//   Map :: (a -> b) -> FreeAp f a -> FreeAp f b
//   Unit :: FreeAp f ()
//   Product :: FreeAp f a -> FreeAp f b -> FreeAp f (a, b)

// instance Functor (FreeAp f) where
//   fmap f fa = Map f fa

// instance Applicative (FreeAp f) where
//   pure a = Map (\_ -> a) Unit
//   fab <*> fa = Map (uncurry ($)) (Product fab fa)

// norm :: FreeAp f a -> FreeAp f a
// norm e = case norm1 e of
//   Map f u ->
//     case norm2 u of
//       Map g v -> Map (f . g) v

// norm1 :: FreeAp f a -> FreeAp f a
// norm1 (Lift fa) = Map id (Lift fa)
// norm1 (Map f e) =
//  case norm1 e of
//    (Map g u) ->  Map(f . g) u
// norm1 Unit = Map id Unit
// norm1 (Product e1 e2) =
//   case (norm1 e1, norm1 e2) of
//     (Map f1 u1, Map f2 u2) -> Map (f1 *** f2) (Product u1 u2)

// norm2 :: FreeAp f a -> FreeAp f a
// norm2 (Lift fa) = Map id (Lift fa)
// norm2 Unit = Map id Unit
// norm2 (Product Unit e2) =
//   case norm2 e2 of
//     Map f2 u2 ->  Map (\x -> ((), f2 x)) u2
// norm2 (Product e1 Unit) =
//   case norm2 e1 of
//     Map f1 u1 ->  Map (\x -> (f1 x, ())) u1
// norm2 (Product e1 (Lift fa)) =
//   case norm2 e1 of
//     Map f1 u1 ->  Map (f1 *** id) (Product u1 $ Lift fa)
// norm2 (Product e1 (Product e2 e3)) =
//   case norm2 (Product (Product e1  e2) e3) of
//     Map f u -> Map (assocr . f) u

// assocr ((x, y), z) = (x, (y, z))
// (***) f g (x, y) = (f x, g y)
//  def p = Zip(Lift(1.some), Lift(2.some))
//  Lifting Operators and Laws, Ralf Hinze, 3.3
  // norm :: Term ρ α → Term ρ α
  // norm e = case norm1 e of Map f u → case norm2 u of Map g v → Map (f ·g) v
  // ----
  // norm1 :: Term ρ α → Term ρ α
  // norm1 (Var n) = Map id (Var n) -- functor identity
  // norm1 (Map f e) = -- functor composition
  //  case norm1 e of Map g u → Map(f ·g) u
  // norm1 Unit = Map id Unit -- functor identity
  // norm1 (e1 :⋆ e2) = -- naturality of ⋆
  //  case (norm1 e1,norm1 e2) of (Map f1 u1, Map f2 u2) → Map (f1 × f2) (u1 :⋆ u2)
  // ----
  // norm2 :: Term ρ α →Term ρ α
  // norm2 (Var n) = Map id (Var n) -- functor identity
  // norm2 Unit = = Map id Unit -- functor identity
  // norm2 (Unit :⋆ e2) =
  //   case norm2 e2 of Map f2 u2 → Map (const () △ f2) u2 -- left identity
  // norm2 (e1 :⋆ Unit) =
  //   case norm2 e1 of Map f1 u1 → Map (f1 △ const ()) u1 -- right identity
  // norm2 (e1 :⋆ Var n) =
  //   case norm2 e1 of Map f1 u1 → Map (f1 × id) (u1 :⋆ Var n)
  // norm2 (e1 :⋆ (e2 :⋆ e3)) =
  //   case norm2 ((e1 :⋆e2):⋆e3) of Map f u → Map (assocr·f) u -- associativity
  // ----
  // id x = x
  // (f ·g)x =f (g x)
  // const x y = x
  // flip f x y = f y x
  // fst (x,y) = x
  // snd (x,y) = y
  // (f △ g) x = (f x, g x)
  // (f × g) (x,y) = (f x, g y)
  // assocl (x, (y,z)) = ((x,y),z)
  // assocr ((x, y), z) = (x, (y, z))
  // app (f,x) = f x
  // curry f x y = f (x,y)
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
// res6: SchemaEx.Schema[SchemaEx.User] =
//   Rec(
//     Map(
//       Zip(
//         Map(
//           One(),
//           SchemaEx$FreeAp$$anon$2$$Lambda$11898/432029629@7452c05d),
//         Map(
//           Zip(
//             Map(
//               Zip(
//                 Map(
//                   One(),
//                   SchemaEx$FreeAp$$anon$2$$Lambda$11898/432029629@44e7ff40),
//                 Lift(Props(id,Num,SchemaEx$$$Lambda$11894/147990481@7f3cb2a))
//               ),SchemaEx$FreeAp$$anon$2$$Lambda$11899/40728788@7a10038b),
//             Lift(Props(name,Str,SchemaEx$$$Lambda$11895/377591062@182274bc))
//           )
//             ,SchemaEx$FreeAp$$anon$2$$Lambda$11899/40728788@7a10038b)
//       ),
//       SchemaEx$FreeAp$$anon$2$$Lambda$11899/40728788@7a10038b)
//   )

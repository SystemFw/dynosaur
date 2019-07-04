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

import model.{AttributeName, AttributeValue}
import Schema.{num, record, str, emptyRecord} // TODO try `Schema => S` rename?

class SchemaSpec extends UnitSpec {
  case class User(id: Int, name: String)
  case class Role(capability: String, user: User)

  sealed trait Status
  case class Error(message: String) extends Status
  case class Auth(role: Role, token: Int) extends Status

  sealed trait State
  case object Open extends State
  case object Closed extends State
  case class Door(state: State)

  sealed trait Same
  case class One(user: User) extends Same
  case class Two(user: User) extends Same

  case class Big(
      one: String,
      two: String,
      three: String,
      four: String,
      five: String,
      six: String,
      seven: String,
      eight: String,
      nine: String,
      ten: String,
      eleven: String,
      twelve: String,
      thirteen: String,
      fourteen: String,
      fifteen: String,
      sixteen: String,
      seventeen: String,
      eighteen: String,
      nineteen: String,
      twenty: String,
      twentyOne: String,
      twentyTwo: String,
      twentyThree: String
  )

  def test[A](schema: Schema[A], data: A, expected: AttributeValue) = {
    def output = Encoder.fromSchema(schema).write(data).toOption.get
    def roundTrip = Decoder.fromSchema(schema).read(output).toOption.get

    assert(output == expected)
    assert(roundTrip == data)
  }

  "schema" should {
    "encode/decode a product" in {
      val user = User(203, "tim")
      val schema = record[User] { field =>
        (
          field("id", num, _.id),
          field("name", str, _.name)
        ).mapN(User.apply)
      }
      val expected = AttributeValue.m(
        AttributeName("id") -> AttributeValue.n(user.id),
        AttributeName("name") -> AttributeValue.s(user.name)
      )

      test(schema, user, expected)
    }

    "encode/decode a product using arbitrary field names" in {
      val user = User(203, "tim")
      val schema = record[User] { field =>
        (
          field("number", num, _.id),
          field("label", str, _.name)
        ).mapN(User.apply)
      }
      val expected = AttributeValue.m(
        AttributeName("number") -> AttributeValue.n(user.id),
        AttributeName("label") -> AttributeValue.s(user.name)
      )

      test(schema, user, expected)
    }

    "encode/decode a product including additional info" in {
      val user = User(203, "tim")
      val versionedSchema = record[User] { field =>
        field("version", str, _ => "1.0") *> (
          field("id", num, _.id),
          field("name", str, _.name)
        ).mapN(User.apply)
      }
      val expected = AttributeValue.m(
        AttributeName("version") -> AttributeValue.s("1.0"),
        AttributeName("id") -> AttributeValue.n(user.id),
        AttributeName("name") -> AttributeValue.s(user.name)
      )

      test(versionedSchema, user, expected)
    }

    "encode/decode a product including additional info, nested" in {
      val user = User(203, "tim")
      val schema = record[User] { field =>
        (
          field("id", num, _.id),
          field("name", str, _.name)
        ).mapN(User.apply)
      }
      val versionedSchema: Schema[User] = record[User] { field =>
        field("version", str, _ => "1.0") *>
          field("body", schema, x => x)
      }
      val expected = AttributeValue.m(
        AttributeName("version") -> AttributeValue.s("1.0"),
        AttributeName("body") -> AttributeValue.m(
          AttributeName("id") -> AttributeValue.n(user.id),
          AttributeName("name") -> AttributeValue.s(user.name)
        )
      )

      test(versionedSchema, user, expected)
    }

    "encode/decode a product with more than 22 fields" in {
      val big = Big(
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f",
        "f"
      )

      val bigSchema = record[Big](
        field =>
          for {
            a <- field("1", str, _.one)
            b <- field("2", str, _.two)
            c <- field("3", str, _.three)
            d <- field("4", str, _.four)
            e <- field("5", str, _.five)
            f <- field("6", str, _.six)
            g <- field("7", str, _.seven)
            h <- field("8", str, _.eight)
            i <- field("9", str, _.nine)
            j <- field("10", str, _.ten)
            k <- field("11", str, _.eleven)
            l <- field("12", str, _.twelve)
            m <- field("13", str, _.thirteen)
            n <- field("14", str, _.fourteen)
            o <- field("15", str, _.fifteen)
            p <- field("16", str, _.sixteen)
            q <- field("17", str, _.seventeen)
            r <- field("18", str, _.eighteen)
            s <- field("19", str, _.nineteen)
            t <- field("20", str, _.twenty)
            u <- field("21", str, _.twentyOne)
            v <- field("22", str, _.twentyTwo)
            w <- field("23", str, _.twentyThree)
          } yield
            Big(
              a,
              b,
              c,
              d,
              e,
              f,
              g,
              h,
              i,
              j,
              k,
              l,
              m,
              n,
              o,
              p,
              q,
              r,
              s,
              t,
              u,
              v,
              w
            )
      )

      val expected = AttributeValue.m(
        AttributeName("1") -> AttributeValue.s(big.one),
        AttributeName("2") -> AttributeValue.s(big.two),
        AttributeName("3") -> AttributeValue.s(big.three),
        AttributeName("4") -> AttributeValue.s(big.four),
        AttributeName("5") -> AttributeValue.s(big.five),
        AttributeName("6") -> AttributeValue.s(big.six),
        AttributeName("7") -> AttributeValue.s(big.seven),
        AttributeName("8") -> AttributeValue.s(big.eight),
        AttributeName("9") -> AttributeValue.s(big.nine),
        AttributeName("10") -> AttributeValue.s(big.ten),
        AttributeName("11") -> AttributeValue.s(big.eleven),
        AttributeName("12") -> AttributeValue.s(big.twelve),
        AttributeName("13") -> AttributeValue.s(big.thirteen),
        AttributeName("14") -> AttributeValue.s(big.fourteen),
        AttributeName("15") -> AttributeValue.s(big.fifteen),
        AttributeName("16") -> AttributeValue.s(big.sixteen),
        AttributeName("17") -> AttributeValue.s(big.seventeen),
        AttributeName("18") -> AttributeValue.s(big.eighteen),
        AttributeName("19") -> AttributeValue.s(big.nineteen),
        AttributeName("20") -> AttributeValue.s(big.twenty),
        AttributeName("21") -> AttributeValue.s(big.twentyOne),
        AttributeName("22") -> AttributeValue.s(big.twentyTwo),
        AttributeName("23") -> AttributeValue.s(big.twentyThree)
      )

      test(bigSchema, big, expected)
    }

    "encode/decode nested ADTs using a discriminator (common scenario)" in {
      val user = User(203, "tim")
      val role = Role("admin", user)
      val error = Error("MyError")
      val auth = Auth(role, 1)

      val userSchema: Schema[User] = record[User] { field =>
        (
          field("id", num, _.id),
          field("name", str, _.name)
        ).mapN(User.apply)
      }
      val roleSchema: Schema[Role] = record[Role] { field =>
        (
          field("capability", str, _.capability),
          field("user", userSchema, _.user)
        ).mapN(Role.apply)
      }
      val statusSchema: Schema[Status] = Schema.oneOf[Status] { alt =>
        val errorSchema = record[Error] { field =>
          field("message", str, _.message).map(Error.apply)
        }

        val authSchema = record[Auth] { field =>
          (
            field("role", roleSchema, _.role),
            field("token", num, _.token)
          ).mapN(Auth.apply)
        }

        alt(errorSchema tag "error") |+| alt(authSchema tag "auth")
      }

      val expectedError = AttributeValue.m(
        AttributeName("error") -> AttributeValue.m(
          AttributeName("message") -> AttributeValue.s(error.message)
        )
      )
      val expectedAuth = AttributeValue.m(
        AttributeName("auth") -> AttributeValue.m(
          AttributeName("role") -> AttributeValue.m(
            AttributeName("capability") -> AttributeValue.s(role.capability),
            AttributeName("user") -> AttributeValue.m(
              AttributeName("id") -> AttributeValue.n(role.user.id),
              AttributeName("name") -> AttributeValue.s(role.user.name)
            )
          ),
          AttributeName("token") -> AttributeValue.n(auth.token)
        )
      )

      test(statusSchema, error, expectedError)
      test(statusSchema, auth, expectedAuth)
    }

    """encode/decode ADTs using an embedded "type" field""" in {
      val user = User(203, "tim")
      val one = One(user)
      val two = Two(user)

      val userSchema: Schema[User] = record { field =>
        (
          field("id", num, _.id),
          field("name", str, _.name)
        ).mapN(User.apply)
      }

      val sameSchema: Schema[Same] = Schema.oneOf { alt =>
        val oneSchema = record[One] { field =>
          field("type", str.const("one", ()), _ => ()) *>
            field("payload", userSchema tag "user", _.user).map(One.apply)
        }

        val twoSchema = record[Two] { field =>
          field("type", str.const("two", ()), _ => ()) *>
            field("payload", userSchema tag "user", _.user).map(Two.apply)
        }

        alt(oneSchema) |+| alt(twoSchema)
      }

      val expectedOne = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("one"),
        AttributeName("payload") -> AttributeValue.m(
          AttributeName("user") -> AttributeValue.m(
            AttributeName("id") -> AttributeValue.n(one.user.id),
            AttributeName("name") -> AttributeValue.s(one.user.name)
          )
        )
      )

      val expectedTwo = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("two"),
        AttributeName("payload") -> AttributeValue.m(
          AttributeName("user") -> AttributeValue.m(
            AttributeName("id") -> AttributeValue.n(one.user.id),
            AttributeName("name") -> AttributeValue.s(one.user.name)
          )
        )
      )

      test(sameSchema, one, expectedOne)
      test(sameSchema, two, expectedTwo)
    }

    "encode/decode objects as empty records" in {
      val openDoor = Door(Open)
      val closedDoor = Door(Closed)

      val stateSchema: Schema[State] = {
        val openSchema = emptyRecord.const((), Open).tag("open")
        val closedSchema = emptyRecord.const((), Closed).tag("closed")

        Schema.oneOf[State] { alt =>
          alt(openSchema) |+| alt(closedSchema)
        }
      }

      val doorSchema = record[Door] { field =>
        field("state", stateSchema, _.state).map(Door.apply)
      }

      val expectedOpen = AttributeValue.m(
        AttributeName("state") -> AttributeValue.m(
          AttributeName("open") -> AttributeValue.m()
        )
      )
      val expectedClosed = AttributeValue.m(
        AttributeName("state") -> AttributeValue.m(
          AttributeName("closed") -> AttributeValue.m()
        )
      )

      test(doorSchema, openDoor, expectedOpen)
      test(doorSchema, closedDoor, expectedClosed)
    }

    "encode/decode objects as strings" in {
      val openDoor = Door(Open)
      val closedDoor = Door(Closed)
      val state = Schema.oneOf[State] { alt =>
        alt { str.const("open", Open) } |+| alt {
          str.const("closed", Closed)
        }
      }

      val doorSchema: Schema[Door] = record { field =>
        field("state", state, _.state).map(Door.apply)
      }
      val expectedOpen = AttributeValue.m(
        AttributeName("state") -> AttributeValue.s("open")
      )
      val expectedClosed = AttributeValue.m(
        AttributeName("state") -> AttributeValue.s("closed")
      )

      test(doorSchema, openDoor, expectedOpen)
      test(doorSchema, closedDoor, expectedClosed)
    }
  }

  val compileTimeInferenceSpec = {
    val userSchema: Schema[User] = record { field =>
      (
        field("id", num, _.id),
        field("name", str, _.name)
      ).mapN(User.apply)
    }

    val userSchema2 = record[User] { field =>
      (
        field("id", num, _.id),
        field("name", str, _.name)
      ).mapN(User.apply)
    }

    // random impl but it does not matter
    def closedSchema: Schema[Closed.type] =
      record(_("foo", str, _.toString).as(Closed))
    def openSchema: Schema[Open.type] =
      record(_("foo", str, _.toString).as(Open))

    val stateSchema: Schema[State] = Schema.oneOf { alt =>
      alt(closedSchema) |+| alt(openSchema)
    }

    val stateSchema2 = Schema.oneOf[State] { alt =>
      alt(closedSchema) |+| alt(openSchema)
    }

    val stateSchema3 = Schema.oneOf[State] { alt =>
      implicit val p1 = Prism.derive[State, Open.type]
      val p2 = Prism.derive[State, Closed.type]

      alt(openSchema) |+| alt(closedSchema)(p2)
    }

    val (_, _, _, _, _) =
      (userSchema, userSchema2, stateSchema, stateSchema2, stateSchema3)
  }
}

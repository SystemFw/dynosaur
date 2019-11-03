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

import model.{AttributeName => Name, AttributeValue => Value}
import Schema.{num, record, str, unit} // TODO try `Schema => S` rename?

class SchemaSpec extends UnitSpec {
  // simple case class
  case class User(id: Int, name: String)
  // nested case class
  case class Role(capability: String, user: User)
  // newtype
  case class TraceToken(value: String)
  // enum
  sealed trait EventType
  case object Started extends EventType
  case object Completed extends EventType
  // case class with enum
  case class Event(state: EventType, value: String)
  // ADT with case classes, objects and ambiguous cases

  /* TODO  revamp ADT example
     - needs to be wrapped in case class (no Map for partition key)
     - mixed case classes and objects
     - two cases should be ambiguous
     - do I need any nesting?
   */

  sealed trait Status
  case class Error(message: String) extends Status
  case class Auth(role: Role, token: Int) extends Status

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

  def test[A](schema: Schema[A], data: A, expected: Value) = {
    def output = Encoder.fromSchema(schema).write(data).toOption.get
    def roundTrip = Decoder.fromSchema(schema).read(output).toOption.get

    assert(output == expected)
    assert(roundTrip == data)
  }

  "schema" should {
    "encode/decode a product (using arbitrary field names)" in {
      val user = User(203, "tim")
      val schema = record[User] { field =>
        (
          field("userId", _.id)(num),
          field("name", _.name)(str)
        ).mapN(User.apply)
      }
      val expected = Value.m(
        Name("userId") -> Value.n(user.id),
        Name("name") -> Value.s(user.name)
      )

      test(schema, user, expected)
    }

    "encode/decode a product including additional structure" in {
      val user = User(203, "tim")
      val schema = record[User] { field =>
        (
          field("id", _.id)(num),
          field("name", _.name)(str)
        ).mapN(User.apply)
      }
      val versionedSchema: Schema[User] = record[User] { field =>
        field.const("version", "1.0")(str) *>
          field("payload", x => x)(schema)
      }
      val expected = Value.m(
        Name("version") -> Value.s("1.0"),
        Name("payload") -> Value.m(
          Name("id") -> Value.n(user.id),
          Name("name") -> Value.s(user.name)
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
            a <- field("1", _.one)(str)
            b <- field("2", _.two)(str)
            c <- field("3", _.three)(str)
            d <- field("4", _.four)(str)
            e <- field("5", _.five)(str)
            f <- field("6", _.six)(str)
            g <- field("7", _.seven)(str)
            h <- field("8", _.eight)(str)
            i <- field("9", _.nine)(str)
            j <- field("10", _.ten)(str)
            k <- field("11", _.eleven)(str)
            l <- field("12", _.twelve)(str)
            m <- field("13", _.thirteen)(str)
            n <- field("14", _.fourteen)(str)
            o <- field("15", _.fifteen)(str)
            p <- field("16", _.sixteen)(str)
            q <- field("17", _.seventeen)(str)
            r <- field("18", _.eighteen)(str)
            s <- field("19", _.nineteen)(str)
            t <- field("20", _.twenty)(str)
            u <- field("21", _.twentyOne)(str)
            v <- field("22", _.twentyTwo)(str)
            w <- field("23", _.twentyThree)(str)
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

      val expected = Value.m(
        Name("1") -> Value.s(big.one),
        Name("2") -> Value.s(big.two),
        Name("3") -> Value.s(big.three),
        Name("4") -> Value.s(big.four),
        Name("5") -> Value.s(big.five),
        Name("6") -> Value.s(big.six),
        Name("7") -> Value.s(big.seven),
        Name("8") -> Value.s(big.eight),
        Name("9") -> Value.s(big.nine),
        Name("10") -> Value.s(big.ten),
        Name("11") -> Value.s(big.eleven),
        Name("12") -> Value.s(big.twelve),
        Name("13") -> Value.s(big.thirteen),
        Name("14") -> Value.s(big.fourteen),
        Name("15") -> Value.s(big.fifteen),
        Name("16") -> Value.s(big.sixteen),
        Name("17") -> Value.s(big.seventeen),
        Name("18") -> Value.s(big.eighteen),
        Name("19") -> Value.s(big.nineteen),
        Name("20") -> Value.s(big.twenty),
        Name("21") -> Value.s(big.twentyOne),
        Name("22") -> Value.s(big.twentyTwo),
        Name("23") -> Value.s(big.twentyThree)
      )

      test(bigSchema, big, expected)
    }

    "encode a newtype with no wrapping" in {
      val token = TraceToken("1234")
      val schema = Schema.str.imap(TraceToken.apply)(_.value)
      val expected = Value.s(token.value)

      test(schema, token, expected)
    }

    "encode/decode enums" in {
      val started = Event(Started, "transaction started event")
      val completed = Event(Completed, "transaction completed event")

      // Similar to the one provided by enumeratum
      def parser: String => Option[EventType] = {
        case "Started" => Started.some
        case "Completed" => Completed.some
        case _ => none
      }

      val stateSchema: Schema[EventType] = Schema.str.imapErr { s =>
        parser(s) toRight ReadError()
      }(_.toString)

      val eventSchema: Schema[Event] = Schema.record { field =>
        (
          field("type", _.state)(stateSchema),
          field("value", _.value)(str)
        ).mapN(Event.apply)
      }

      val expectedStarted = Value.m(
        Name("type") -> Value.s("Started"),
        Name("value") -> Value.s(started.value)
      )

      val expectedCompleted = Value.m(
        Name("type") -> Value.s("Completed"),
        Name("value") -> Value.s(completed.value)
      )

      test(eventSchema, started, expectedStarted)
      test(eventSchema, completed, expectedCompleted)
    }

    "encode/decode nested ADTs using a discriminator" in {
      val user = User(203, "tim")
      val role = Role("admin", user)
      val error = Error("MyError")
      val auth = Auth(role, 1)

      val userSchema: Schema[User] = record[User] { field =>
        (
          field("id", _.id)(num),
          field("name", _.name)(str)
        ).mapN(User.apply)
      }
      val roleSchema: Schema[Role] = record[Role] { field =>
        (
          field("capability", _.capability)(str),
          field("user", _.user)(userSchema)
        ).mapN(Role.apply)
      }
      val statusSchema: Schema[Status] = Schema.oneOf[Status] { alt =>
        val errorSchema = record[Error] { field =>
          field("message", _.message)(str).map(Error.apply)
        }

        val authSchema = record[Auth] { field =>
          (
            field("role", _.role)(roleSchema),
            field("token", _.token)(num)
          ).mapN(Auth.apply)
        }

        alt(errorSchema tag "error") |+| alt(authSchema tag "auth")
      }

      val expectedError = Value.m(
        Name("error") -> Value.m(
          Name("message") -> Value.s(error.message)
        )
      )
      val expectedAuth = Value.m(
        Name("auth") -> Value.m(
          Name("role") -> Value.m(
            Name("capability") -> Value.s(role.capability),
            Name("user") -> Value.m(
              Name("id") -> Value.n(role.user.id),
              Name("name") -> Value.s(role.user.name)
            )
          ),
          Name("token") -> Value.n(auth.token)
        )
      )

      test(statusSchema, error, expectedError)
      test(statusSchema, auth, expectedAuth)
    }

    """encode/decode ADTs using an embedded "type" field""" in {
      // TODO remove the tagging here, a bit more orthogonal testing
      val user = User(203, "tim")
      val one = One(user)
      val two = Two(user)

      val userSchema: Schema[User] = record { field =>
        (
          field("id", _.id)(num),
          field("name", _.name)(str)
        ).mapN(User.apply)
      }

      val sameSchema: Schema[Same] = Schema.oneOf { alt =>
        val oneSchema = record[One] { field =>
          field.const("type", "one")(str) *>
            field("user", _.user)(userSchema).map(One.apply)
        }

        val twoSchema = record[Two] { field =>
          field.const("type", "two")(str) *>
            field("user", _.user)(userSchema).map(Two.apply)
        }

        alt(oneSchema) |+| alt(twoSchema)
      }

      val expectedOne = Value.m(
        Name("type") -> Value.s("one"),
        Name("user") -> Value.m(
          Name("id") -> Value.n(one.user.id),
          Name("name") -> Value.s(one.user.name)
        )
      )

      val expectedTwo = Value.m(
        Name("type") -> Value.s("two"),
        Name("user") -> Value.m(
          Name("id") -> Value.n(one.user.id),
          Name("name") -> Value.s(one.user.name)
        )
      )

      test(sameSchema, one, expectedOne)
      test(sameSchema, two, expectedTwo)
    }

    // "encode/decode objects as empty records (e.g. for use in mixed ADTs)" in {
    //   // TODO rename to encode decode objects as empty records or Strings, remove const
    //   // revamp ADT tests altogether
    //   val openDoor = Door(Open)
    //   val closedDoor = Door(Closed)

    //   val stateSchema: Schema[State] = {
    //     val openSchema =
    //       unit.tag("open").imap(_ => Open)(_ => ())
    //     val closedSchema = unit.tag("closed").imap(_ => Open)(_ => ())

    //     Schema.oneOf[State] { alt =>
    //       alt(openSchema) |+| alt(closedSchema)
    //     }
    //   }

    //   val doorSchema = record[Door] { field =>
    //     field("state", _.state)(stateSchema).map(Door.apply)
    //   }

    //   val expectedOpen = Value.m(
    //     Name("state") -> Value.m(
    //       Name("open") -> Value.m()
    //     )
    //   )
    //   val expectedClosed = Value.m(
    //     Name("state") -> Value.m(
    //       Name("closed") -> Value.m()
    //     )
    //   )

    //   test(doorSchema, openDoor, expectedOpen)
    //   test(doorSchema, closedDoor, expectedClosed)
    // }

    // "encode/decode objects as strings" in {
    //   // TODO merge with the above
    //   val openDoor = Door(Open)
    //   val closedDoor = Door(Closed)
    //   val state = Schema.oneOf[State] { alt =>
    //     alt { str.const("open", Open) } |+| alt {
    //       str.const("closed", Closed)
    //     }
    //   }

    //   val doorSchema: Schema[Door] = record { field =>
    //     field("state", _.state)(state).map(Door.apply)
    //   }
    //   val expectedOpen = Value.m(
    //     Name("state") -> Value.s("open")
    //   )
    //   val expectedClosed = Value.m(
    //     Name("state") -> Value.s("closed")
    //   )

    //   test(doorSchema, openDoor, expectedOpen)
    //   test(doorSchema, closedDoor, expectedClosed)
    // }

  }

  val compileTimeInferenceSpec = {
    val userSchema: Schema[User] = record { field =>
      (
        field("id", _.id)(num),
        field("name", _.name)(str)
      ).mapN(User.apply)
    }

    val userSchema2 = record[User] { field =>
      (
        field("id", _.id)(num),
        field("name", _.name)(str)
      ).mapN(User.apply)
    }

    // random impl but it does not matter
    def completedSchema: Schema[Completed.type] =
      record(_("foo", _.toString)(str).as(Completed))
    def startedSchema: Schema[Started.type] =
      record(_("foo", _.toString)(str).as(Started))

    val eventTypeSchema: Schema[EventType] = Schema.oneOf { alt =>
      alt(completedSchema) |+| alt(startedSchema)
    }

    val eventTypeSchema2 = Schema.oneOf[EventType] { alt =>
      alt(completedSchema) |+| alt(startedSchema)
    }

    val eventTypeSchema3 = Schema.oneOf[EventType] { alt =>
      implicit val p1 = Prism.derive[EventType, Started.type]
      val p2 = Prism.derive[EventType, Completed.type]

      alt(startedSchema) |+| alt(completedSchema)(p2)
    }

    val traceTokenSchema = Schema.str.imap(TraceToken.apply)(_.value)

    val (_, _, _, _, _, _) =
      (
        userSchema,
        userSchema2,
        eventTypeSchema,
        eventTypeSchema2,
        eventTypeSchema3,
        traceTokenSchema
      )
  }
}

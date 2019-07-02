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

  //TODO test this for different ADT encoding
  sealed trait Same
  case class One(v: String) extends Same
  case class Two(v: String) extends Same

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

    "encode/decode (nested) ADTs using a discriminator" in {
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

    """encode/decode (nested) ADTs using an embedded "type" field""" in {
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
      val statusSchema: Schema[Status] = {
        val errorSchema = Schema.record[Error] { field =>
          field("type", str, _ => "error") *>
            field("message", str, _.message).map(Error.apply)
        }

        val authSchema = Schema.record[Auth] { field =>
          field("type", str, _ => "auth") *>
            (
              field("role", roleSchema, _.role),
              field("token", num, _.token)
            ).mapN(Auth.apply)
        }

        Schema.oneOf[Status] { alt =>
          alt(errorSchema) |+| alt(authSchema)
        }
      }

      val expectedError = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("error"),
        AttributeName("message") -> AttributeValue.s(error.message)
      )
      val expectedAuth = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("auth"),
        AttributeName("role") -> AttributeValue.m(
          AttributeName("capability") -> AttributeValue.s(role.capability),
          AttributeName("user") -> AttributeValue.m(
            AttributeName("id") -> AttributeValue.n(role.user.id),
            AttributeName("name") -> AttributeValue.s(role.user.name)
          )
        ),
        AttributeName("token") -> AttributeValue.n(auth.token)
      )

      test(statusSchema, error, expectedError)
      test(statusSchema, auth, expectedAuth)
    }

    "encode/decode objects as empty records" in {
      val openDoor = Door(Open)
      val closedDoor = Door(Closed)

      val stateSchema: Schema[State] = {
        val openSchema = record[Open.type] { field =>
          field("open", emptyRecord, _ => ()).as(Open)
        }
        val closedSchema = Schema.record[Closed.type] { field =>
          field("closed", emptyRecord, _ => ()).as(Closed)
        }
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
      pendingUntilFixed {
        info("Needs some notion of partial iso")
        val openDoor = Door(Open)
        val closedDoor = Door(Closed)

        val stateSchema: Schema[State] = {
          val openSchema = record[Open.type] { field =>
            field("open", emptyRecord, _ => ()).as(Open)
          }
          val closedSchema = Schema.record[Closed.type] { field =>
            field("closed", emptyRecord, _ => ()).as(Closed)
          }
          Schema.oneOf[State] { alt =>
            alt(openSchema) |+| alt(closedSchema)
          }
        }
        val doorSchema = record[Door] { field =>
          field("state", stateSchema, _.state).map(Door.apply)
        }

        val expectedOpen = AttributeValue.m(
          AttributeName("state") -> AttributeValue.s("open")
        )
        val expectedClosed = AttributeValue.m(
          AttributeName("state") -> AttributeValue.s("closed")
        )

        test(doorSchema, openDoor, expectedOpen)
        test(doorSchema, closedDoor, expectedClosed)
        ()
      }
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

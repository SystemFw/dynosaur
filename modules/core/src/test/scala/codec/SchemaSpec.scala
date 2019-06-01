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
import com.ovoenergy.comms.aws.common._
import dynosaur.codec.Schema.{num, record, str}
import dynosaur.model.{AttributeName, AttributeValue}

class SchemaSpec extends UnitSpec {

  "schema" should {
    "encode/decode a product" in {
      val expectedEncodedUser = AttributeValue.m(
        AttributeName("id") -> AttributeValue.n(user.id),
        AttributeName("name") -> AttributeValue.s(user.name)
      )

      val encodedUser = userSchema.write(user)
      val decodedUser = userSchema.read(encodedUser)

      assert(encodedUser === expectedEncodedUser)
      assert(decodedUser === user)
    }

    "encode/decode a product using an \"type\" field" in {
      val taggedUserSchema: Schema[User] = Schema.record[User] { field =>
        field.const("type", str, "user") *>
          field.id("body", userSchema)
      }

      val expectedEncodedUser = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("user"),
        AttributeName("body") -> AttributeValue.m(
          AttributeName("id") -> AttributeValue.n(user.id),
          AttributeName("name") -> AttributeValue.s(user.name)
        )
      )

      val encodedUser = taggedUserSchema.write(user)
      val decodedUser = taggedUserSchema.read(encodedUser)

      assert(encodedUser === expectedEncodedUser)
      assert(decodedUser === user)
    }

    "encode/decode a sum" in {
      val fooSchema: Schema[Foo] = {
        val barSchema: Schema[Bar.type] = Schema.record[Bar.type] { _ =>
          Schema.structure.Ap.pure(Bar)
        }

        val bazSchema: Schema[Baz.type] = Schema.record[Baz.type] { _ =>
          Schema.structure.Ap.pure(Baz)
        }

        Schema.oneOf[Foo] { alt =>
          alt("bar", barSchema)(_ => Bar) { case Bar => Bar } |+|
            alt("baz", bazSchema)(_ => Baz) { case Baz => Baz }
        }
      }

      val expectedEncodedBar = AttributeValue.m(
        Map(
          AttributeName("bar") -> AttributeValue.m()
        )
      )

      val expectedEncodedBaz = AttributeValue.m(
        Map(
          AttributeName("baz") -> AttributeValue.m()
        )
      )

      val encodedBar = fooSchema.write(Bar)
      val encodedBaz = fooSchema.write(Baz)

      assert(encodedBar === expectedEncodedBar)
      assert(encodedBaz === expectedEncodedBaz)

      val decodedBar = fooSchema.read(encodedBar)
      val decodedBaz = fooSchema.read(encodedBaz)

      assert(decodedBar === Bar)
      assert(decodedBaz === Baz)
    }

    "encode/decode ADTs using a discriminator" in {
      val errorStatus = Error("MyError")
      val authStatus = Auth(role)

      val expectedEncodedErrorStatus = AttributeValue.m(
        AttributeName("error") -> AttributeValue.s(errorStatus.message)
      )

      val expectedEncodedAuthStatus = AttributeValue.m(
        AttributeName("auth") -> AttributeValue.m(
          AttributeName("capability") -> AttributeValue.s(role.capability),
          AttributeName("user") -> AttributeValue.m(
            AttributeName("id") -> AttributeValue.n(role.user.id),
            AttributeName("name") -> AttributeValue.s(role.user.name)
          )
        )
      )

      val encodedErrorStatus = statusSchema.write(errorStatus)
      val encodedAuthStatus = statusSchema.write(authStatus)

      assert(encodedErrorStatus === expectedEncodedErrorStatus)
      assert(encodedAuthStatus === expectedEncodedAuthStatus)

      val decodedErrorStatus = statusSchema.read(encodedErrorStatus)
      val decodedAuthStatus = statusSchema.read(encodedAuthStatus)

      assert(decodedErrorStatus === errorStatus)
      assert(decodedAuthStatus === authStatus)
    }

    "encode/decode ADTs using an embedded \"type\" field" in {
      val taggedStatusSchema: Schema[Status] = {
        val errorSchema: Schema[String] = Schema.record[String] { field =>
          field.const("type", str, "error") *>
            field.id("body", str)
        }

        val authSchema: Schema[Role] = Schema.record[Role] { field =>
          field.const("type", str, "auth") *>
            field.id("body", roleSchema)
        }

        Schema.oneOf[Status] { alt =>
          alt.from(errorSchema)(Error(_)) { case Error(v) => v } |+|
            alt.from(authSchema)(Auth(_)) { case Auth(v) => v }
        }
      }

      val errorStatus = Error("MyError")
      val authStatus = Auth(role)

      val expectedEncodedErrorStatus = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("error"),
        AttributeName("body") -> AttributeValue.s(errorStatus.message)
      )

      val expectedEncodedAuthStatus = AttributeValue.m(
        AttributeName("type") -> AttributeValue.s("auth"),
        AttributeName("body") -> AttributeValue.m(
          AttributeName("capability") -> AttributeValue.s(role.capability),
          AttributeName("user") -> AttributeValue.m(
            AttributeName("id") -> AttributeValue.n(role.user.id),
            AttributeName("name") -> AttributeValue.s(role.user.name)
          )
        )
      )

      val encodedErrorStatus = taggedStatusSchema.write(errorStatus)
      val encodedAuthStatus = taggedStatusSchema.write(authStatus)

      assert(encodedErrorStatus === expectedEncodedErrorStatus)
      assert(encodedAuthStatus === expectedEncodedAuthStatus)

      val decodedErrorStatus = taggedStatusSchema.read(encodedErrorStatus)
      val decodedAuthStatus = taggedStatusSchema.read(encodedAuthStatus)

      assert(decodedErrorStatus === errorStatus)
      assert(decodedAuthStatus === authStatus)
    }
  }

  case class User(id: Int, name: String)
  case class Role(capability: String, user: User)

  sealed trait Status
  case class Error(message: String) extends Status
  case class Auth(role: Role) extends Status

  sealed trait Foo
  case object Bar extends Foo
  case object Baz extends Foo

  private val userSchema: Schema[User] = record[User] { field =>
    (
      field("id", num, _.id),
      field("name", str, _.name)
    ).mapN(User.apply)
  }

  private val roleSchema: Schema[Role] = record[Role] { field =>
    (
      field("capability", str, _.capability),
      field("user", userSchema, _.user)
    ).mapN(Role.apply)
  }

  private val statusSchema: Schema[Status] = Schema.oneOf[Status] { alt =>
    alt("error", str)(Error(_)) { case Error(v) => v } |+|
      alt("auth", roleSchema)(Auth(_)) { case Auth(v) => v }
  }

  private val user = User(20, "joe")
  private val role = Role("admin", user)

  implicit class CodecSyntax[A](schema: Schema[A]) {
    def read(v: AttributeValue): A =
      Decoder.fromSchema(schema).read(v).toOption.get

    def write(v: A): AttributeValue =
      Encoder.fromSchema(schema).write(v).toOption.get
  }

}

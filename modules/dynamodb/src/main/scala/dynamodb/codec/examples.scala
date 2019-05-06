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

import cats._, implicits._

import Schema._

object examples {
  case class User(id: Int, name: String)
  case class Role(capability: String, u: User)
  sealed trait Status
  case class Error(s: String) extends Status
  case class Auth(r: Role) extends Status

  def userSchema: Schema[User] = record[User] { field =>
    (
      field("id", num, _.id),
      field("name", str, _.name)
    ).mapN(User.apply)
  }

  def roleSchema: Schema[Role] = record[Role] { field =>
    (
      field("capability", str, _.capability),
      field("user", userSchema, _.u)
    ).mapN(Role.apply)
  }

  def role = Role("admin", User(20, "joe"))

  // Encodes ADTs using a discriminator, written from scratch
  val taggedStatus = {
    val error = record[String](field => field.id("error", str))
    val auth = record[Role](field => field.id("auth", roleSchema))

    oneOf[Status] { alt =>
      alt.from(error)(Error(_)) { case Error(v) => v } |+|
        alt.from(auth)(Auth(_)) { case Auth(v) => v }
    }
  }

  // encodes ADTs using an embedded "type" field, written from scratch
  val untaggedStatus = {
    val error = record[String] { field =>
      field.const("type", str, "error") *> field.id("body", str)
    }
    val auth = record[Role] { field =>
      field.const("type", str, "auth") *> field.id("body", roleSchema)
    }

    oneOf[Status] { alt =>
      alt.from(error)(Error(_)) { case Error(v) => v } |+|
        alt.from(auth)(Auth(_)) { case Auth(v) => v }
    }
  }

  // Encodes ADTs using a discriminator, with helper
  val statusSchema: Schema[Status] = oneOf[Status] { alt =>
    alt("error", str)(Error(_)) { case Error(v) => v } |+|
      alt("auth", roleSchema)(Auth(_)) { case Auth(v) => v }
  }

  // combinator code to show that the above can be abstracted
  val combinators = {
    val error1 = record[String](field => field.id("error", str))
    val auth1 = record[Role](field => field.id("auth", roleSchema))

    val error2 = record[String] { field =>
      field.const("type", str, "error") *> field.id("body", str)
    }
    val auth2 = record[Role] { field =>
      field.const("type", str, "auth") *> field.id("body", roleSchema)
    }

    val _ = (error2, auth1) // to suppress unused warning

    def choices(err: Schema[String], auth: Schema[Role]) = oneOf[Status] {
      alt =>
        alt("error", err)(Error(_)) { case Error(v) => v } |+|
          alt("auth", auth)(Auth(_)) { case Auth(v) => v }
    }

    choices(error1, auth2)
  }

  // encodeds ADTs with embedded "type" field, with helper TODO

  val a = roleSchema.write(role)
  val b = roleSchema.read(a)
  val c = statusSchema.write(Error("MyError"))
  val d = statusSchema.write(Auth(role))
  val e = statusSchema.read(c)
  val f = statusSchema.read(d)
  val g = untaggedStatus.write(Auth(role))
  val h = untaggedStatus.read(g)

  val inference = {
    // Cannot infer type of function
    // field[Role]("capability", str, _.capability)
    // tag[Status]("auth", roleSchema)(Auth(_)) { case Auth(v) => v }

    // can ascribe manually
    field("capability", str, (_: Role).capability)
    alt("auth", roleSchema)(Auth(_): Status) { case Auth(v) => v }
    alt.from(roleSchema)(Auth(_): Status) { case Auth(v) => v }

    // the library helps you
    field[Role]("capability", str, _.capability)
    alt[Status]("auth", roleSchema)(Auth(_)) { case Auth(v) => v }
    alt[Status].from(roleSchema)(Auth(_)) { case Auth(v) => v }
  }

  import com.ovoenergy.comms.aws.dynamodb.model.AttributeValue

  implicit class CodecSyntax[A](schema: Schema[A]) {
    def read(v: AttributeValue): A =
      Decoder.fromSchema(schema).read(v).toOption.get

    def write(v: A): AttributeValue =
      Encoder.fromSchema(schema).write(v).toOption.get
  }

  implicit class PrettyPrinter(v: AttributeValue) {
    import com.ovoenergy.comms.aws.dynamodb.codec._
    import io.circe.syntax._

    def pp: String = v.asJson.spaces2
  }
}

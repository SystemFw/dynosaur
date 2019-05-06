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

  def userSchema: Schema[User] = rec(
    (
      field[User]("id", num, _.id),
      field[User]("name", str, _.name)
    ).mapN(User.apply)
  )

  def roleSchema: Schema[Role] =
    rec(
      (
        field[Role]("capability", str, _.capability),
        field[Role]("user", userSchema, _.u)
      ).mapN(Role.apply)
    )

  def role = Role("admin", User(20, "joe"))

  // Encodes ADTs using a discriminator
  val statusSchema: Schema[Status] = oneOf(
    tag[Status]("error", str)(Error(_)) { case Error(v) => v },
    tag[Status]("auth", roleSchema)(Auth(_)) { case Auth(v) => v }
  )

  // encodes ADTs using an embedded "type" field
  val untaggedStatus = oneOf(
    alt[Status](
      rec(
        field[String]("type", str, _ => "error") *>
          field[String]("body", str, x => x)
      )
    )(Error(_)) { case Error(v) => v },
    alt[Status](
      rec(
        field[Role]("type", str, _ => "auth") *>
          field[Role]("body", roleSchema, x => x)
      )
    )(Auth(_)) { case Auth(v) => v }
  )

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
    tag("auth", roleSchema)(Auth(_): Status) { case Auth(v) => v }
    alt(roleSchema)(Auth(_): Status) { case Auth(v) => v }

    // the library helps you
    field[Role]("capability", str, _.capability)
    tag[Status]("auth", roleSchema)(Auth(_)) { case Auth(v) => v }
    alt[Status](roleSchema)(Auth(_)) { case Auth(v) => v }
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

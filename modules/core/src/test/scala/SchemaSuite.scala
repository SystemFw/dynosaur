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

import cats.implicits._

import scodec.bits.ByteVector

import munit.ScalaCheckSuite

import org.scalacheck.Prop._

import Arbitraries._

class SchemaSuite extends ScalaCheckSuite {
  /* simple case class */
  case class User(id: Int, name: String)
  /* nested case class */
  case class Role(capability: String, user: User)
  /* case class with Options */
  case class Log(msg: String, tag: Option[String])
  /* newtype */
  case class TraceToken(value: String)
  /* enum */
  sealed trait EventType
  case object Started extends EventType
  case object Completed extends EventType
  /* case class with enum */
  case class Event(state: EventType, value: String)
  /* ADT with case classes, objects and ambiguous cases */
  sealed trait Status
  case class Error(msg: String, cause: String) extends Status
  case class Warning(msg: String, cause: String) extends Status
  case object Unknown extends Status
  case class Successful(link: String, expires: Int) extends Status
  /* top level wrapper for ADT */
  // Used to test the discriminator key encoding, since in real life
  // you cannot have a Map as partition key
  case class Upload(id: String, status: Status)
  /* more than 22 fields, above tuple and function restriction */
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

  def check[A](schema: Schema[A], data: A, expected: Value) = {
    def output = Encoder.fromSchema(schema).write(data).toOption.get
    def roundTrip = Decoder.fromSchema(schema).read(output).toOption.get

    assertEquals(output, expected)
    assertEquals(roundTrip, data)
  }

  test("encode/decode ints") {
    forAll { (i: Int) =>
      val expected = Value.n(i)
      check(Schema[Int], i, expected)
    }
  }

  test("encode/decode longs") {
    forAll { (l: Long) =>
      val expected = Value.n(l)
      check(Schema[Long], l, expected)
    }
  }

  test("encode/decode doubles") {
    forAll { (d: Double) =>
      val expected = Value.n(d)
      check(Schema[Double], d, expected)
    }
  }

  test("encode/decode floats") {
    forAll { (f: Float) =>
      val expected = Value.n(f)
      check(Schema[Float], f, expected)
    }
  }

  test("encode/decode shorts") {
    forAll { (s: Short) =>
      val expected = Value.n(s)
      check(Schema[Short], s, expected)
    }
  }

  test("encode/decode strings") {
    forAll { (s: String) =>
      val expected = Value.s(s)
      check(Schema[String], s, expected)
    }
  }

  test("encode/decode booleans") {
    forAll { (b: Boolean) =>
      val expected = Value.bool(b)
      check(Schema[Boolean], b, expected)
    }
  }

  test("encode/decode lists") {
    forAll { (l: List[Int]) =>
      val expected = Value.l(l.map(Value.n))
      check(Schema[List[Int]], l, expected)
    }
  }

  test("encode/decode vectors") {
    forAll { (l: Vector[String]) =>
      val expected = Value.l(l.map(Value.s))
      check(Schema[Vector[String]], l, expected)
    }
  }

  test("encode/decode bytes") {
    forAll { (b: Array[Byte]) =>
      val in = ByteVector(b)
      val expected = Value.b(in)
      check(Schema[ByteVector], in, expected)
    }
  }

  test("encode/decode Maps") {
    forAll { (m: Map[String, Int]) =>
      val expected = Value.m {
        m.map { case (k, v) => k -> Value.n(v) }
      }
      check(Schema[Map[String, Int]], m, expected)
    }
  }

  test("encode/decode a product") {
    val role = Role("admin", User(203, "tim"))
    val schema: Schema[Role] = Schema.record { field =>
      (
        field("capability", _.capability),
        field("user", _.user) { // nesting
          Schema.record { field =>
            (
              field("id", _.id),
              field("firstName", _.name) // renaming
            ).mapN(User.apply)
          }
        }
      ).mapN(Role.apply)
    }
    val expected = Value.m(
      "capability" -> Value.s(role.capability),
      "user" -> Value.m(
        "id" -> Value.n(role.user.id),
        "firstName" -> Value.s(role.user.name)
      )
    )

    check(schema, role, expected)
  }

  test("encode/decode a product including additional structure") {
    val user = User(203, "tim")
    val schema = Schema.record[User] { field =>
      (
        field("id", _.id),
        field("name", _.name)
      ).mapN(User.apply)
    }
    val versionedSchema: Schema[User] = Schema.record[User] { field =>
      field.const("version", "1.0") *> field("payload", x => x)(schema)
    }
    val expected = Value.m(
      "version" -> Value.s("1.0"),
      "payload" -> Value.m(
        "id" -> Value.n(user.id),
        "name" -> Value.s(user.name)
      )
    )

    check(versionedSchema, user, expected)
  }

  test("encode/decode a product containing optional fields") {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field.opt("tag", _.tag)
      ).mapN(Log.apply)
    }

    val expectedComplete = Value.m(
      "msg" -> Value.s(complete.msg),
      "tag" -> Value.s(complete.tag.get)
    )

    val expectedNoTag = Value.m(
      "msg" -> Value.s(noTag.msg)
    )

    val incorrectNoTag = Value.m(
      "msg" -> Value.s(noTag.msg),
      "tag" -> Value.nul
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      Decoder
        .fromSchema(schema)
        .read(incorrectNoTag),
      Left(ReadError())
    )
  }

  test("encode/decode a product containing nullable values") {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field("tag", _.tag)(Schema.nullable)
      ).mapN(Log.apply)
    }

    val expectedComplete = Value.m(
      "msg" -> Value.s(complete.msg),
      "tag" -> Value.s(complete.tag.get)
    )

    val expectedNoTag = Value.m(
      "msg" -> Value.s(noTag.msg),
      "tag" -> Value.nul
    )

    val incorrectNoTag = Value.m(
      "msg" -> Value.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      Decoder
        .fromSchema(schema)
        .read(incorrectNoTag),
      Left(ReadError())
    )
  }

  test(
    "encode/decode a product containing optional fields, with leniency to nullability"
  ) {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field
          .opt("tag", _.tag.map(_.some))(Schema.nullable)
          .map(_.flatten)
      ).mapN(Log.apply)
    }

    val expectedComplete = Value.m(
      "msg" -> Value.s(complete.msg),
      "tag" -> Value.s(complete.tag.get)
    )

    val expectedNoTag = Value.m(
      "msg" -> Value.s(noTag.msg)
    )

    val acceptedNoTag = Value.m(
      "msg" -> Value.s(noTag.msg),
      "tag" -> Value.nul
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      Decoder
        .fromSchema(schema)
        .read(acceptedNoTag),
      Right(noTag)
    )
  }

  test(
    "encode/decode a product containing nullable values, with leniency to optionality"
  ) {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field
          .opt("tag", _.tag.some)(Schema.nullable)
          .map(_.flatten)
      ).mapN(Log.apply)
    }

    val expectedComplete = Value.m(
      "msg" -> Value.s(complete.msg),
      "tag" -> Value.s(complete.tag.get)
    )

    val expectedNoTag = Value.m(
      "msg" -> Value.s(noTag.msg),
      "tag" -> Value.nul
    )

    val acceptedtNoTag = Value.m(
      "msg" -> Value.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      Decoder
        .fromSchema(schema)
        .read(acceptedtNoTag),
      Right(noTag)
    )
  }

  test("encode/decode a product with more than 22 fields") {
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

    val bigSchema = Schema.record[Big](field =>
      for {
        a <- field("1", _.one)
        b <- field("2", _.two)
        c <- field("3", _.three)
        d <- field("4", _.four)
        e <- field("5", _.five)
        f <- field("6", _.six)
        g <- field("7", _.seven)
        h <- field("8", _.eight)
        i <- field("9", _.nine)
        j <- field("10", _.ten)
        k <- field("11", _.eleven)
        l <- field("12", _.twelve)
        m <- field("13", _.thirteen)
        n <- field("14", _.fourteen)
        o <- field("15", _.fifteen)
        p <- field("16", _.sixteen)
        q <- field("17", _.seventeen)
        r <- field("18", _.eighteen)
        s <- field("19", _.nineteen)
        t <- field("20", _.twenty)
        u <- field("21", _.twentyOne)
        v <- field("22", _.twentyTwo)
        w <- field("23", _.twentyThree)
      } yield Big(
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
      "1" -> Value.s(big.one),
      "2" -> Value.s(big.two),
      "3" -> Value.s(big.three),
      "4" -> Value.s(big.four),
      "5" -> Value.s(big.five),
      "6" -> Value.s(big.six),
      "7" -> Value.s(big.seven),
      "8" -> Value.s(big.eight),
      "9" -> Value.s(big.nine),
      "10" -> Value.s(big.ten),
      "11" -> Value.s(big.eleven),
      "12" -> Value.s(big.twelve),
      "13" -> Value.s(big.thirteen),
      "14" -> Value.s(big.fourteen),
      "15" -> Value.s(big.fifteen),
      "16" -> Value.s(big.sixteen),
      "17" -> Value.s(big.seventeen),
      "18" -> Value.s(big.eighteen),
      "19" -> Value.s(big.nineteen),
      "20" -> Value.s(big.twenty),
      "21" -> Value.s(big.twentyOne),
      "22" -> Value.s(big.twentyTwo),
      "23" -> Value.s(big.twentyThree)
    )

    check(bigSchema, big, expected)
  }

  test("encode a newtype with no wrapping") {
    val token = TraceToken("1234")
    val schema = Schema[String].imap(TraceToken.apply)(_.value)
    val expected = Value.s(token.value)

    check(schema, token, expected)
  }

  test("encode/decode enums") {
    val started = Event(Started, "transaction started event")
    val completed = Event(Completed, "transaction completed event")

    // Similar to the one provided by enumeratum
    def parser: String => Option[EventType] = {
      case "Started" => Started.some
      case "Completed" => Completed.some
      case _ => none
    }

    val stateSchema: Schema[EventType] = Schema[String].imapErr { s =>
      parser(s) toRight ReadError()
    }(_.toString)

    val eventSchema: Schema[Event] = Schema.record { field =>
      (
        field("type", _.state)(stateSchema),
        field("value", _.value)
      ).mapN(Event.apply)
    }

    val expectedStarted = Value.m(
      "type" -> Value.s("Started"),
      "value" -> Value.s(started.value)
    )

    val expectedCompleted = Value.m(
      "type" -> Value.s("Completed"),
      "value" -> Value.s(completed.value)
    )

    check(eventSchema, started, expectedStarted)
    check(eventSchema, completed, expectedCompleted)
  }

  test("encode/decode an ADT using a discriminator key") {
    val schema: Schema[Upload] = {
      val error = Schema
        .record[Error] { field =>
          (
            field("msg", _.msg),
            field("cause", _.cause)
          ).mapN(Error.apply)
        }
        .tag("error")

      val warning = Schema
        .record[Warning] { field =>
          (
            field("msg", _.msg),
            field("cause", _.cause)
          ).mapN(Warning.apply)
        }
        .tag("warning")

      val unknown =
        Schema
          .record[Unknown.type](
            _.pure(Unknown)
          )
          .tag("unknown")

      val successful = Schema
        .record[Successful] { field =>
          (
            field("link", _.link),
            field("expires", _.expires)
          ).mapN(Successful.apply)
        }
        .tag("successful")

      Schema.record[Upload] { field =>
        (
          field("id", _.id),
          field("status", _.status) {
            Schema.oneOf { alt =>
              alt(error) |+| alt(warning) |+| alt(unknown) |+| alt(successful)
            }
          }
        ).mapN(Upload.apply)
      }
    }

    val error = Error("error msg", "error cause")
    val errorUp = Upload("error id", error)
    val warning = Warning("warning msg", "warning cause")
    val warningUp = Upload("warning id", warning)
    val unknownUp = Upload("unknown id", Unknown)
    val successful = Successful("link", 150)
    val successfulUp = Upload("successful id", successful)

    val expectedError = Value.m(
      "id" -> Value.s(errorUp.id),
      "status" -> Value.m(
        "error" -> Value.m(
          "msg" -> Value.s(error.msg),
          "cause" -> Value.s(error.cause)
        )
      )
    )
    val expectedWarning = Value.m(
      "id" -> Value.s(warningUp.id),
      "status" -> Value.m(
        "warning" -> Value.m(
          "msg" -> Value.s(warning.msg),
          "cause" -> Value.s(warning.cause)
        )
      )
    )
    val expectedUnknown = Value.m(
      "id" -> Value.s(unknownUp.id),
      "status" -> Value.m(
        "unknown" -> Value.m()
      )
    )
    val expectedSuccessful = Value.m(
      "id" -> Value.s(successfulUp.id),
      "status" -> Value.m(
        "successful" -> Value.m(
          "link" -> Value.s(successful.link),
          "expires" -> Value.n(successful.expires)
        )
      )
    )

    check(schema, errorUp, expectedError)
    check(schema, warningUp, expectedWarning)
    check(schema, unknownUp, expectedUnknown)
    check(schema, successfulUp, expectedSuccessful)
  }

  test("encode/decode an ADT using a discriminator field") {
    val schema: Schema[Upload] = {
      val error = Schema
        .record[Error] { field =>
          field.const("type", "error") *> (
            field("msg", _.msg),
            field("cause", _.cause)
          ).mapN(Error.apply)
        }

      val warning = Schema
        .record[Warning] { field =>
          field.const("type", "warning") *> (
            field("msg", _.msg),
            field("cause", _.cause)
          ).mapN(Warning.apply)
        }

      val unknown =
        Schema.record[Unknown.type](
          _.const("type", "unknown").as(Unknown)
        )

      val successful = Schema
        .record[Successful] { field =>
          field.const("type", "successful") *> (
            field("link", _.link),
            field("expires", _.expires)
          ).mapN(Successful.apply)
        }

      Schema.record[Upload] { field =>
        (
          field("id", _.id),
          field("status", _.status) {
            Schema.oneOf { alt =>
              alt(error) |+| alt(warning) |+| alt(unknown) |+| alt(successful)
            }
          }
        ).mapN(Upload.apply)
      }
    }

    val error = Error("error msg", "error cause")
    val errorUp = Upload("error id", error)
    val warning = Warning("warning msg", "warning cause")
    val warningUp = Upload("warning id", warning)
    val unknownUp = Upload("unknown id", Unknown)
    val successful = Successful("link", 150)
    val successfulUp = Upload("successful id", successful)

    val expectedError = Value.m(
      "id" -> Value.s(errorUp.id),
      "status" -> Value.m(
        "type" -> Value.s("error"),
        "msg" -> Value.s(error.msg),
        "cause" -> Value.s(error.cause)
      )
    )
    val expectedWarning = Value.m(
      "id" -> Value.s(warningUp.id),
      "status" -> Value.m(
        "type" -> Value.s("warning"),
        "msg" -> Value.s(warning.msg),
        "cause" -> Value.s(warning.cause)
      )
    )
    val expectedUnknown = Value.m(
      "id" -> Value.s(unknownUp.id),
      "status" -> Value.m(
        "type" -> Value.s("unknown")
      )
    )
    val expectedSuccessful = Value.m(
      "id" -> Value.s(successfulUp.id),
      "status" -> Value.m(
        "type" -> Value.s("successful"),
        "link" -> Value.s(successful.link),
        "expires" -> Value.n(successful.expires)
      )
    )

    check(schema, errorUp, expectedError)
    check(schema, warningUp, expectedWarning)
    check(schema, unknownUp, expectedUnknown)
    check(schema, successfulUp, expectedSuccessful)
  }

  test("pass through attribute value untouched") {
    forAll { (v: Value) =>
      check(Schema[Value], v, v)
    }
  }

  val compileTimeInferenceSpec = {
    val userSchema: Schema[User] = Schema.record { field =>
      (
        field("id", _.id),
        field("name", _.name)
      ).mapN(User.apply)
    }

    val userSchema2 = Schema.record[User] { field =>
      (
        field("id", _.id),
        field("name", _.name)
      ).mapN(User.apply)
    }

    implicit val traceTokenSchema: Schema[TraceToken] =
      Schema[String].imap(TraceToken.apply)(_.value)

    // random impl but it does not matter
    def completedSchema: Schema[Completed.type] =
      Schema.record(_("foo", _.toString).as(Completed))
    def startedSchema: Schema[Started.type] =
      Schema.record(_("foo", _.toString).as(Started))

    val eventTypeSchema: Schema[EventType] = Schema.oneOf { alt =>
      alt(completedSchema) |+| alt(startedSchema)
    }

    val eventTypeSchema2 = Schema.oneOf[EventType] { alt =>
      alt(completedSchema) |+| alt(startedSchema)
    }

    val eventTypeSchema3 = Schema.oneOf[EventType] { alt =>
      implicit val p1: Prism[EventType, Started.type] =
        Prism.derive[EventType, Started.type]
      val p2 = Prism.derive[EventType, Completed.type]

      alt(startedSchema) |+| alt(completedSchema)(p2)
    }

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

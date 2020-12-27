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
  /* Recursive products */
  case class Department(name: String, subdeps: List[Department])
  /* Recursive ADT */
  sealed trait Text
  case class Paragraph(text: String) extends Text
  case class Section(title: String, contents: Text) extends Text

  def check[A](schema: Schema[A], data: A, expected: DynamoValue) = {
    def output = schema.write(data).toOption.get
    def roundTrip = schema.read(output).toOption.get

    assertEquals(output, expected)
    assertEquals(roundTrip, data)
  }

  test("ints") {
    forAll { (i: Int) =>
      val expected = DynamoValue.n(i)
      check(Schema[Int], i, expected)
    }
  }

  test("longs") {
    forAll { (l: Long) =>
      val expected = DynamoValue.n(l)
      check(Schema[Long], l, expected)
    }
  }

  test("doubles") {
    forAll { (d: Double) =>
      val expected = DynamoValue.n(d)
      check(Schema[Double], d, expected)
    }
  }

  test("floats") {
    forAll { (f: Float) =>
      val expected = DynamoValue.n(f)
      check(Schema[Float], f, expected)
    }
  }

  test("shorts") {
    forAll { (s: Short) =>
      val expected = DynamoValue.n(s)
      check(Schema[Short], s, expected)
    }
  }

  test("strings") {
    forAll { (s: String) =>
      val expected = DynamoValue.s(s)
      check(Schema[String], s, expected)
    }
  }

  test("booleans") {
    forAll { (b: Boolean) =>
      val expected = DynamoValue.bool(b)
      check(Schema[Boolean], b, expected)
    }
  }

  test("lists") {
    forAll { (l: List[Int]) =>
      val expected = DynamoValue.l(l.map(i => DynamoValue.n(i)))
      check(Schema[List[Int]], l, expected)
    }
  }

  test("vectors") {
    forAll { (l: List[String]) =>
      val expected = DynamoValue.l(l.map(DynamoValue.s))
      check(Schema[List[String]], l, expected)
    }
  }

  test("bytes") {
    forAll { (b: Array[Byte]) =>
      val in = ByteVector(b)
      val expected = DynamoValue.b(in)
      check(Schema[ByteVector], in, expected)
    }
  }

  test("maps") {
    forAll { (m: Map[String, Int]) =>
      val expected = DynamoValue.m {
        m.map { case (k, v) => k -> DynamoValue.n(v) }
      }
      check(Schema[Map[String, Int]], m, expected)
    }
  }

  test("products") {
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
    val expected = DynamoValue.m(
      "capability" -> DynamoValue.s(role.capability),
      "user" -> DynamoValue.m(
        "id" -> DynamoValue.n(role.user.id),
        "firstName" -> DynamoValue.s(role.user.name)
      )
    )

    check(schema, role, expected)
  }

  test("products with additional structure") {
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
    val expected = DynamoValue.m(
      "version" -> DynamoValue.s("1.0"),
      "payload" -> DynamoValue.m(
        "id" -> DynamoValue.n(user.id),
        "name" -> DynamoValue.s(user.name)
      )
    )

    check(versionedSchema, user, expected)
  }

  test("products with optional fields") {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field.opt("tag", _.tag)
      ).mapN(Log.apply)
    }

    val expectedComplete = DynamoValue.m(
      "msg" -> DynamoValue.s(complete.msg),
      "tag" -> DynamoValue.s(complete.tag.get)
    )

    val expectedNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg)
    )

    val incorrectNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg),
      "tag" -> DynamoValue.nul
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(incorrectNoTag),
      Left(Schema.ReadError())
    )
  }

  test("products with nullable values") {
    val complete = Log("complete log", "tag".some)
    val noTag = Log("incomplete log", None)

    val schema = Schema.record[Log] { field =>
      (
        field("msg", _.msg),
        field("tag", _.tag)(Schema.nullable)
      ).mapN(Log.apply)
    }

    val expectedComplete = DynamoValue.m(
      "msg" -> DynamoValue.s(complete.msg),
      "tag" -> DynamoValue.s(complete.tag.get)
    )

    val expectedNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg),
      "tag" -> DynamoValue.nul
    )

    val incorrectNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(incorrectNoTag),
      Left(Schema.ReadError())
    )
  }

  test(
    "products with optional fields, leniency to nullability"
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

    val expectedComplete = DynamoValue.m(
      "msg" -> DynamoValue.s(complete.msg),
      "tag" -> DynamoValue.s(complete.tag.get)
    )

    val expectedNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg)
    )

    val acceptedNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg),
      "tag" -> DynamoValue.nul
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(acceptedNoTag),
      Right(noTag)
    )
  }

  test("products with nullable values, leniency to optionality") {
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

    val expectedComplete = DynamoValue.m(
      "msg" -> DynamoValue.s(complete.msg),
      "tag" -> DynamoValue.s(complete.tag.get)
    )

    val expectedNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg),
      "tag" -> DynamoValue.nul
    )

    val acceptedtNoTag = DynamoValue.m(
      "msg" -> DynamoValue.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(acceptedtNoTag),
      Right(noTag)
    )
  }

  test("products with more than 22 fields") {
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

    val expected = DynamoValue.m(
      "1" -> DynamoValue.s(big.one),
      "2" -> DynamoValue.s(big.two),
      "3" -> DynamoValue.s(big.three),
      "4" -> DynamoValue.s(big.four),
      "5" -> DynamoValue.s(big.five),
      "6" -> DynamoValue.s(big.six),
      "7" -> DynamoValue.s(big.seven),
      "8" -> DynamoValue.s(big.eight),
      "9" -> DynamoValue.s(big.nine),
      "10" -> DynamoValue.s(big.ten),
      "11" -> DynamoValue.s(big.eleven),
      "12" -> DynamoValue.s(big.twelve),
      "13" -> DynamoValue.s(big.thirteen),
      "14" -> DynamoValue.s(big.fourteen),
      "15" -> DynamoValue.s(big.fifteen),
      "16" -> DynamoValue.s(big.sixteen),
      "17" -> DynamoValue.s(big.seventeen),
      "18" -> DynamoValue.s(big.eighteen),
      "19" -> DynamoValue.s(big.nineteen),
      "20" -> DynamoValue.s(big.twenty),
      "21" -> DynamoValue.s(big.twentyOne),
      "22" -> DynamoValue.s(big.twentyTwo),
      "23" -> DynamoValue.s(big.twentyThree)
    )

    check(bigSchema, big, expected)
  }

  test("newtypes, with no wrapping") {
    val token = TraceToken("1234")
    val schema = Schema[String].imap(TraceToken.apply)(_.value)
    val expected = DynamoValue.s(token.value)

    check(schema, token, expected)
  }

  test("enums") {
    val started = Event(Started, "transaction started event")
    val completed = Event(Completed, "transaction completed event")

    // Similar to the one provided by enumeratum
    def parser: String => Option[EventType] = {
      case "Started" => Started.some
      case "Completed" => Completed.some
      case _ => none
    }

    val stateSchema: Schema[EventType] = Schema[String].imapErr { s =>
      parser(s) toRight Schema.ReadError()
    }(_.toString)

    val eventSchema: Schema[Event] = Schema.record { field =>
      (
        field("type", _.state)(stateSchema),
        field("value", _.value)
      ).mapN(Event.apply)
    }

    val expectedStarted = DynamoValue.m(
      "type" -> DynamoValue.s("Started"),
      "value" -> DynamoValue.s(started.value)
    )

    val expectedCompleted = DynamoValue.m(
      "type" -> DynamoValue.s("Completed"),
      "value" -> DynamoValue.s(completed.value)
    )

    check(eventSchema, started, expectedStarted)
    check(eventSchema, completed, expectedCompleted)
  }

  test("ADTs, via a discriminator key") {
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

    val expectedError = DynamoValue.m(
      "id" -> DynamoValue.s(errorUp.id),
      "status" -> DynamoValue.m(
        "error" -> DynamoValue.m(
          "msg" -> DynamoValue.s(error.msg),
          "cause" -> DynamoValue.s(error.cause)
        )
      )
    )
    val expectedWarning = DynamoValue.m(
      "id" -> DynamoValue.s(warningUp.id),
      "status" -> DynamoValue.m(
        "warning" -> DynamoValue.m(
          "msg" -> DynamoValue.s(warning.msg),
          "cause" -> DynamoValue.s(warning.cause)
        )
      )
    )
    val expectedUnknown = DynamoValue.m(
      "id" -> DynamoValue.s(unknownUp.id),
      "status" -> DynamoValue.m(
        "unknown" -> DynamoValue.m()
      )
    )
    val expectedSuccessful = DynamoValue.m(
      "id" -> DynamoValue.s(successfulUp.id),
      "status" -> DynamoValue.m(
        "successful" -> DynamoValue.m(
          "link" -> DynamoValue.s(successful.link),
          "expires" -> DynamoValue.n(successful.expires)
        )
      )
    )

    check(schema, errorUp, expectedError)
    check(schema, warningUp, expectedWarning)
    check(schema, unknownUp, expectedUnknown)
    check(schema, successfulUp, expectedSuccessful)
  }

  test("ADTs, via a discriminator field") {
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

    val expectedError = DynamoValue.m(
      "id" -> DynamoValue.s(errorUp.id),
      "status" -> DynamoValue.m(
        "type" -> DynamoValue.s("error"),
        "msg" -> DynamoValue.s(error.msg),
        "cause" -> DynamoValue.s(error.cause)
      )
    )
    val expectedWarning = DynamoValue.m(
      "id" -> DynamoValue.s(warningUp.id),
      "status" -> DynamoValue.m(
        "type" -> DynamoValue.s("warning"),
        "msg" -> DynamoValue.s(warning.msg),
        "cause" -> DynamoValue.s(warning.cause)
      )
    )
    val expectedUnknown = DynamoValue.m(
      "id" -> DynamoValue.s(unknownUp.id),
      "status" -> DynamoValue.m(
        "type" -> DynamoValue.s("unknown")
      )
    )
    val expectedSuccessful = DynamoValue.m(
      "id" -> DynamoValue.s(successfulUp.id),
      "status" -> DynamoValue.m(
        "type" -> DynamoValue.s("successful"),
        "link" -> DynamoValue.s(successful.link),
        "expires" -> DynamoValue.n(successful.expires)
      )
    )

    check(schema, errorUp, expectedError)
    check(schema, warningUp, expectedWarning)
    check(schema, unknownUp, expectedUnknown)
    check(schema, successfulUp, expectedSuccessful)
  }

  test("pass through attribute value untouched") {
    forAll { (v: DynamoValue) =>
      check(Schema[DynamoValue], v, v)
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

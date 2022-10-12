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

import cats.syntax.all._
import scodec.bits.ByteVector

import dynosaur.{DynamoValue => V}

import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import dynosaur.Arbitraries._

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
  case class Department(name: String, subdeps: List[Department] = Nil)
  /* Recursive ADT */
  sealed trait Text
  case class Paragraph(text: String) extends Text
  case class Section(title: String, contents: List[Text]) extends Text

  def check[A](schema: Schema[A], data: A, expected: V) = {

    val output = schema
      .write(data)
      .toOption

    assertEquals(output, expected.some, clue(data))

    val roundTrip = output.flatMap { output =>
      schema.read(output).toOption
    }

    assertEquals(roundTrip, data.some)
  }

  test("id") {
    forAllNoShrink { (dv: V) =>
      val expected = dv
      check(Schema[V], dv, expected)
    }
  }

  // TODO Cannot test equality in AttributeValue
  test("AttributeValue".ignore) {
    forAll { (av: AttributeValue) =>
      val expected = DynamoValue(av)
      check(Schema[AttributeValue], av, expected)
    }
  }

  test("ints") {
    forAll { (i: Int) =>
      val expected = V.n(i)
      check(Schema[Int], i, expected)
    }
  }

  test("longs") {
    forAll { (l: Long) =>
      val expected = V.n(l)
      check(Schema[Long], l, expected)
    }
  }

  test("doubles") {
    forAll { (d: Double) =>
      val expected = V.n(d)
      check(Schema[Double], d, expected)
    }
  }

  test("floats") {
    forAll { (f: Float) =>
      val expected = V.n(f)
      check(Schema[Float], f, expected)
    }
  }

  test("shorts") {
    forAll { (s: Short) =>
      val expected = V.n(s)
      check(Schema[Short], s, expected)
    }
  }

  test("strings") {
    forAll { (s: String) =>
      val expected = V.s(s)
      check(Schema[String], s, expected)
    }
  }

  test("booleans") {
    forAll { (b: Boolean) =>
      val expected = V.bool(b)
      check(Schema[Boolean], b, expected)
    }
  }

  test("lists") {
    forAll { (l: List[Int]) =>
      val expected = V.l(l.map(i => V.n(i)))
      check(Schema[List[Int]], l, expected)
    }
  }

  test("vectors") {
    forAll { (l: List[String]) =>
      val expected = V.l(l.map(V.s))
      check(Schema[List[String]], l, expected)
    }
  }

  test("bytes") {
    forAll { (b: Array[Byte]) =>
      val in = ByteVector(b)
      val expected = V.b(in)
      check(Schema[ByteVector], in, expected)
    }
  }

  test("maps") {
    forAll { (m: Map[String, Int]) =>
      val expected = V.m {
        m.map { case (k, v) => k -> V.n(v) }
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
    val expected = V.m(
      "capability" -> V.s(role.capability),
      "user" -> V.m(
        "id" -> V.n(role.user.id),
        "firstName" -> V.s(role.user.name)
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
    val expected = V.m(
      "version" -> V.s("1.0"),
      "payload" -> V.m(
        "id" -> V.n(user.id),
        "name" -> V.s(user.name)
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

    val expectedComplete = V.m(
      "msg" -> V.s(complete.msg),
      "tag" -> V.s(complete.tag.get)
    )

    val expectedNoTag = V.m(
      "msg" -> V.s(noTag.msg)
    )

    val incorrectNoTag = V.m(
      "msg" -> V.s(noTag.msg),
      "tag" -> V.nul
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(incorrectNoTag),
      Left(Schema.ReadError("value \"NULL\": true is not a String"))
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

    val expectedComplete = V.m(
      "msg" -> V.s(complete.msg),
      "tag" -> V.s(complete.tag.get)
    )

    val expectedNoTag = V.m(
      "msg" -> V.s(noTag.msg),
      "tag" -> V.nul
    )

    val incorrectNoTag = V.m(
      "msg" -> V.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(incorrectNoTag),
      Left(Schema.ReadError("required field tag does not contain a value"))
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

    val expectedComplete = V.m(
      "msg" -> V.s(complete.msg),
      "tag" -> V.s(complete.tag.get)
    )

    val expectedNoTag = V.m(
      "msg" -> V.s(noTag.msg)
    )

    val acceptedNoTag = V.m(
      "msg" -> V.s(noTag.msg),
      "tag" -> V.nul
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

    val expectedComplete = V.m(
      "msg" -> V.s(complete.msg),
      "tag" -> V.s(complete.tag.get)
    )

    val expectedNoTag = V.m(
      "msg" -> V.s(noTag.msg),
      "tag" -> V.nul
    )

    val acceptedtNoTag = V.m(
      "msg" -> V.s(noTag.msg)
    )

    check(schema, complete, expectedComplete)
    check(schema, noTag, expectedNoTag)
    assertEquals(
      schema.read(acceptedtNoTag),
      Right(noTag)
    )
  }

  test("recursive products with defer") {
    val departments = Department(
      "STEM",
      List(
        Department("CS"),
        Department(
          "Maths",
          List(
            Department("Applied"),
            Department("Theoretical")
          )
        )
      )
    )

    val expected = V.m(
      "name" -> V.s("STEM"),
      "subdeps" -> V.l(
        V.m(
          "name" -> V.s("CS"),
          "subdeps" -> V.l()
        ),
        V.m(
          "name" -> V.s("Maths"),
          "subdeps" -> V.l(
            V.m(
              "name" -> V.s("Applied"),
              "subdeps" -> V.l()
            ),
            V.m(
              "name" -> V.s("Theoretical"),
              "subdeps" -> V.l()
            )
          )
        )
      )
    )

    lazy val schema: Schema[Department] = Schema.record { field =>
      (
        field("name", _.name),
        field("subdeps", _.subdeps)(Schema.defer(schema.asList))
      ).mapN(Department.apply)
    }

    check(schema, departments, expected)
  }

  test("recursive products with recursive") {
    val departments = Department(
      "STEM",
      List(
        Department("CS"),
        Department(
          "Maths",
          List(
            Department("Applied"),
            Department("Theoretical")
          )
        )
      )
    )

    val expected = V.m(
      "name" -> V.s("STEM"),
      "subdeps" -> V.l(
        V.m(
          "name" -> V.s("CS"),
          "subdeps" -> V.l()
        ),
        V.m(
          "name" -> V.s("Maths"),
          "subdeps" -> V.l(
            V.m(
              "name" -> V.s("Applied"),
              "subdeps" -> V.l()
            ),
            V.m(
              "name" -> V.s("Theoretical"),
              "subdeps" -> V.l()
            )
          )
        )
      )
    )

    val schema: Schema[Department] = Schema.recursive { rec =>
      Schema.record { field =>
        (
          field("name", _.name),
          field("subdeps", _.subdeps)(rec.asList)
        ).mapN(Department.apply)
      }
    }

    check(schema, departments, expected)
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

    val bigSchema = Schema.record[Big] { field =>
      (
        field("1", _.one),
        field("2", _.two),
        field("3", _.three),
        field("4", _.four),
        field("5", _.five),
        field("6", _.six),
        field("7", _.seven),
        field("8", _.eight),
        field("9", _.nine),
        field("10", _.ten),
        field("11", _.eleven),
        field("12", _.twelve),
        field("13", _.thirteen),
        field("14", _.fourteen),
        field("15", _.fifteen),
        field("16", _.sixteen),
        field("17", _.seventeen),
        field("18", _.eighteen),
        field("19", _.nineteen),
        field("20", _.twenty),
        field("21", _.twentyOne),
        (
          field("22", _.twentyTwo),
          field("23", _.twentyThree)
        ).tupled
      ).mapN {
        case (
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
              (v, w)
            ) =>
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
      }
    }

    val expected = V.m(
      "1" -> V.s(big.one),
      "2" -> V.s(big.two),
      "3" -> V.s(big.three),
      "4" -> V.s(big.four),
      "5" -> V.s(big.five),
      "6" -> V.s(big.six),
      "7" -> V.s(big.seven),
      "8" -> V.s(big.eight),
      "9" -> V.s(big.nine),
      "10" -> V.s(big.ten),
      "11" -> V.s(big.eleven),
      "12" -> V.s(big.twelve),
      "13" -> V.s(big.thirteen),
      "14" -> V.s(big.fourteen),
      "15" -> V.s(big.fifteen),
      "16" -> V.s(big.sixteen),
      "17" -> V.s(big.seventeen),
      "18" -> V.s(big.eighteen),
      "19" -> V.s(big.nineteen),
      "20" -> V.s(big.twenty),
      "21" -> V.s(big.twentyOne),
      "22" -> V.s(big.twentyTwo),
      "23" -> V.s(big.twentyThree)
    )

    check(bigSchema, big, expected)
  }

  test("newtypes, with no wrapping") {
    val token = TraceToken("1234")
    val schema = Schema[String].imap(TraceToken.apply)(_.value)
    val expected = V.s(token.value)

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
      parser(s) toRight Schema.ReadError("")
    }(_.toString)

    val eventSchema: Schema[Event] = Schema.record { field =>
      (
        field("type", _.state)(stateSchema),
        field("value", _.value)
      ).mapN(Event.apply)
    }

    val expectedStarted = V.m(
      "type" -> V.s("Started"),
      "value" -> V.s(started.value)
    )

    val expectedCompleted = V.m(
      "type" -> V.s("Completed"),
      "value" -> V.s(completed.value)
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

    val expectedError = V.m(
      "id" -> V.s(errorUp.id),
      "status" -> V.m(
        "error" -> V.m(
          "msg" -> V.s(error.msg),
          "cause" -> V.s(error.cause)
        )
      )
    )
    val expectedWarning = V.m(
      "id" -> V.s(warningUp.id),
      "status" -> V.m(
        "warning" -> V.m(
          "msg" -> V.s(warning.msg),
          "cause" -> V.s(warning.cause)
        )
      )
    )
    val expectedUnknown = V.m(
      "id" -> V.s(unknownUp.id),
      "status" -> V.m(
        "unknown" -> V.m()
      )
    )
    val expectedSuccessful = V.m(
      "id" -> V.s(successfulUp.id),
      "status" -> V.m(
        "successful" -> V.m(
          "link" -> V.s(successful.link),
          "expires" -> V.n(successful.expires)
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

    val expectedError = V.m(
      "id" -> V.s(errorUp.id),
      "status" -> V.m(
        "type" -> V.s("error"),
        "msg" -> V.s(error.msg),
        "cause" -> V.s(error.cause)
      )
    )
    val expectedWarning = V.m(
      "id" -> V.s(warningUp.id),
      "status" -> V.m(
        "type" -> V.s("warning"),
        "msg" -> V.s(warning.msg),
        "cause" -> V.s(warning.cause)
      )
    )
    val expectedUnknown = V.m(
      "id" -> V.s(unknownUp.id),
      "status" -> V.m(
        "type" -> V.s("unknown")
      )
    )
    val expectedSuccessful = V.m(
      "id" -> V.s(successfulUp.id),
      "status" -> V.m(
        "type" -> V.s("successful"),
        "link" -> V.s(successful.link),
        "expires" -> V.n(successful.expires)
      )
    )

    check(schema, errorUp, expectedError)
    check(schema, warningUp, expectedWarning)
    check(schema, unknownUp, expectedUnknown)
    check(schema, successfulUp, expectedSuccessful)
  }

  test("recursive ADTs with defer") {
    val text = Section(
      "A",
      List(
        Paragraph("lorem ipsum"),
        Section(
          "A.b",
          List(Paragraph("dolor sit amet"))
        )
      )
    )

    val expected = V.m(
      "section" -> V.m(
        "title" -> V.s("A"),
        "contents" -> V.l(
          V.m(
            "paragraph" -> V.m(
              "text" -> V.s("lorem ipsum")
            )
          ),
          V.m(
            "section" -> V.m(
              "title" -> V.s("A.b"),
              "contents" -> V.l(
                V.m(
                  "paragraph" -> V.m(
                    "text" -> V.s("dolor sit amet")
                  )
                )
              )
            )
          )
        )
      )
    )

    lazy val schema: Schema[Text] = Schema.oneOf[Text] { alt =>
      val paragraph = Schema
        .record[Paragraph] { field =>
          field("text", _.text).map(Paragraph.apply)
        }
        .tag("paragraph")

      val section = Schema
        .record[Section] { field =>
          (
            field("title", _.title),
            field("contents", _.contents)(Schema.defer(schema.asList))
          ).mapN(Section.apply)
        }
        .tag("section")

      alt(section) |+| alt(paragraph)
    }

    check(schema, text, expected)
  }

  test("recursive ADTs with recursive") {
    val text = Section(
      "A",
      List(
        Paragraph("lorem ipsum"),
        Section(
          "A.b",
          List(Paragraph("dolor sit amet"))
        )
      )
    )

    val expected = V.m(
      "section" -> V.m(
        "title" -> V.s("A"),
        "contents" -> V.l(
          V.m(
            "paragraph" -> V.m(
              "text" -> V.s("lorem ipsum")
            )
          ),
          V.m(
            "section" -> V.m(
              "title" -> V.s("A.b"),
              "contents" -> V.l(
                V.m(
                  "paragraph" -> V.m(
                    "text" -> V.s("dolor sit amet")
                  )
                )
              )
            )
          )
        )
      )
    )

    val schema: Schema[Text] = Schema.recursive { rec =>
      Schema.oneOf { alt =>
        val paragraph = Schema
          .record[Paragraph] { field =>
            field("text", _.text).map(Paragraph.apply)
          }
          .tag("paragraph")

        val section = Schema
          .record[Section] { field =>
            (
              field("title", _.title),
              field("contents", _.contents)(rec.asList)
            ).mapN(Section.apply)
          }
          .tag("section")

        alt(section) |+| alt(paragraph)
      }
    }

    check(schema, text, expected)
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
    val completedSchema: Schema[Completed.type] =
      Schema.record(_("foo", _.toString).as(Completed))
    val startedSchema: Schema[Started.type] =
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

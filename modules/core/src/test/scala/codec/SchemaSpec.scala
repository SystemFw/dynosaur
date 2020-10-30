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

import scodec.bits.ByteVector

import model.{AttributeName => Name, AttributeValue => Value}
import Arbitraries._

// TODO change this to flatSpec
class SchemaSpec extends UnitSpec {
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

  def test[A](schema: Schema[A], data: A, expected: Value) = {
    def output = Encoder.fromSchema(schema).write(data).toOption.get
    def roundTrip = Decoder.fromSchema(schema).read(output).toOption.get

    assert(output == expected)
    assert(roundTrip == data)
  }

  "schema" should {
    "encode/decode ints" in forAll { i: Int =>
      val expected = Value.n(i)
      test(Schema[Int], i, expected)
    }

    "encode/decode longs" in forAll { l: Long =>
      val expected = Value.n(l)
      test(Schema[Long], l, expected)
    }

    "encode/decode doubles" in forAll { d: Double =>
      val expected = Value.n(d)
      test(Schema[Double], d, expected)
    }

    "encode/decode floats" in forAll { f: Float =>
      val expected = Value.n(f)
      test(Schema[Float], f, expected)
    }

    "encode/decode shorts" in forAll { s: Short =>
      val expected = Value.n(s)
      test(Schema[Short], s, expected)
    }

    "encode/decode strings" in forAll { s: String =>
      val expected = Value.s(s)
      test(Schema[String], s, expected)
    }

    "encode/decode booleans" in forAll { b: Boolean =>
      val expected = Value.bool(b)
      test(Schema[Boolean], b, expected)
    }

    "encode/decode lists" in forAll { l: List[Int] =>
      val expected = Value.l(l.map(Value.n))
      test(Schema[List[Int]], l, expected)
    }

    "encode/decode vectors" in forAll { l: Vector[String] =>
      val expected = Value.l(l.map(Value.s))
      test(Schema[Vector[String]], l, expected)
    }

    "encode/decode bytes" in forAll { b: Array[Byte] =>
      val in = ByteVector(b)
      val expected = Value.b(in)
      test(Schema[ByteVector], in, expected)
    }

    "encode/decode Maps" in forAll { m: Map[String, Int] =>
      val expected = Value.m {
        m.map { case (k, v) => Name(k) -> Value.n(v) }
      }
      test(Schema[Map[String, Int]], m, expected)
    }

    "encode/decode a product" in {
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
        Name("capability") -> Value.s(role.capability),
        Name("user") -> Value.m(
          Name("id") -> Value.n(role.user.id),
          Name("firstName") -> Value.s(role.user.name)
        )
      )

      test(schema, role, expected)
    }

    "encode/decode a product including additional structure" in {
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
        Name("version") -> Value.s("1.0"),
        Name("payload") -> Value.m(
          Name("id") -> Value.n(user.id),
          Name("name") -> Value.s(user.name)
        )
      )

      test(versionedSchema, user, expected)
    }

    "encode/decode a product containing optional fields" in {
      val complete = Log("complete log", "tag".some)
      val noTag = Log("incomplete log", None)

      val schema = Schema.record[Log] { field =>
        (
          field("msg", _.msg),
          field.opt("tag", _.tag)
        ).mapN(Log.apply)
      }

      val expectedComplete = Value.m(
        Name("msg") -> Value.s(complete.msg),
        Name("tag") -> Value.s(complete.tag.get)
      )

      val expectedNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg)
      )

      val incorrectNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg),
        Name("tag") -> Value.`null`
      )

      test(schema, complete, expectedComplete)
      test(schema, noTag, expectedNoTag)
      Decoder
        .fromSchema(schema)
        .read(incorrectNoTag) shouldBe Left(ReadError())
    }

    "encode/decode a product containing nullable values" in {
      val complete = Log("complete log", "tag".some)
      val noTag = Log("incomplete log", None)

      val schema = Schema.record[Log] { field =>
        (
          field("msg", _.msg),
          field("tag", _.tag)(Schema.nullable)
        ).mapN(Log.apply)
      }

      val expectedComplete = Value.m(
        Name("msg") -> Value.s(complete.msg),
        Name("tag") -> Value.s(complete.tag.get)
      )

      val expectedNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg),
        Name("tag") -> Value.`null`
      )

      val incorrectNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg)
      )

      test(schema, complete, expectedComplete)
      test(schema, noTag, expectedNoTag)
      Decoder
        .fromSchema(schema)
        .read(incorrectNoTag) shouldBe Left(ReadError())
    }

    "encode/decode a product containing optional fields, with leniency to nullability" in {
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
        Name("msg") -> Value.s(complete.msg),
        Name("tag") -> Value.s(complete.tag.get)
      )

      val expectedNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg)
      )

      val acceptedNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg),
        Name("tag") -> Value.`null`
      )

      test(schema, complete, expectedComplete)
      test(schema, noTag, expectedNoTag)
      Decoder
        .fromSchema(schema)
        .read(acceptedNoTag) shouldBe Right(noTag)
    }

    "encode/decode a product containing nullable values, with leniency to optionality" in {
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
        Name("msg") -> Value.s(complete.msg),
        Name("tag") -> Value.s(complete.tag.get)
      )

      val expectedNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg),
        Name("tag") -> Value.`null`
      )

      val acceptedtNoTag = Value.m(
        Name("msg") -> Value.s(noTag.msg)
      )

      test(schema, complete, expectedComplete)
      test(schema, noTag, expectedNoTag)
      Decoder
        .fromSchema(schema)
        .read(acceptedtNoTag) shouldBe Right(noTag)
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

      val bigSchema = Schema.record[Big](
        field =>
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
      val schema = Schema[String].imap(TraceToken.apply)(_.value)
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

    "encode/decode an ADT using a discriminator key" in {
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
        Name("id") -> Value.s(errorUp.id),
        Name("status") -> Value.m(
          Name("error") -> Value.m(
            Name("msg") -> Value.s(error.msg),
            Name("cause") -> Value.s(error.cause)
          )
        )
      )
      val expectedWarning = Value.m(
        Name("id") -> Value.s(warningUp.id),
        Name("status") -> Value.m(
          Name("warning") -> Value.m(
            Name("msg") -> Value.s(warning.msg),
            Name("cause") -> Value.s(warning.cause)
          )
        )
      )
      val expectedUnknown = Value.m(
        Name("id") -> Value.s(unknownUp.id),
        Name("status") -> Value.m(
          Name("unknown") -> Value.m()
        )
      )
      val expectedSuccessful = Value.m(
        Name("id") -> Value.s(successfulUp.id),
        Name("status") -> Value.m(
          Name("successful") -> Value.m(
            Name("link") -> Value.s(successful.link),
            Name("expires") -> Value.n(successful.expires)
          )
        )
      )

      test(schema, errorUp, expectedError)
      test(schema, warningUp, expectedWarning)
      test(schema, unknownUp, expectedUnknown)
      test(schema, successfulUp, expectedSuccessful)
    }

    "encode/decode an ADT using a discriminator field" in {
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
        Name("id") -> Value.s(errorUp.id),
        Name("status") -> Value.m(
          Name("type") -> Value.s("error"),
          Name("msg") -> Value.s(error.msg),
          Name("cause") -> Value.s(error.cause)
        )
      )
      val expectedWarning = Value.m(
        Name("id") -> Value.s(warningUp.id),
        Name("status") -> Value.m(
          Name("type") -> Value.s("warning"),
          Name("msg") -> Value.s(warning.msg),
          Name("cause") -> Value.s(warning.cause)
        )
      )
      val expectedUnknown = Value.m(
        Name("id") -> Value.s(unknownUp.id),
        Name("status") -> Value.m(
          Name("type") -> Value.s("unknown")
        )
      )
      val expectedSuccessful = Value.m(
        Name("id") -> Value.s(successfulUp.id),
        Name("status") -> Value.m(
          Name("type") -> Value.s("successful"),
          Name("link") -> Value.s(successful.link),
          Name("expires") -> Value.n(successful.expires)
        )
      )

      test(schema, errorUp, expectedError)
      test(schema, warningUp, expectedWarning)
      test(schema, unknownUp, expectedUnknown)
      test(schema, successfulUp, expectedSuccessful)
    }

    "pass through attribute value untouched" in forAll { v: Value =>
      test(Schema[Value], v, v)
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

    implicit val traceTokenSchema =
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
      implicit val p1 = Prism.derive[EventType, Started.type]
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

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

import scodec.bits.ByteVector
import dynosaur.{DynamoValue => V}
import munit.FunSuite

class DynamoValueSuite extends FunSuite {
  test("pretty prints strings") {
    val v = V.s("Hello")

    val expected = """
    |"S": "Hello"
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints numbers") {
    val v = V.n(123.45)

    val expected = """
    |"N": "123.45"
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints bools") {
    val v = V.bool(true)

    val expected = """
    |"BOOL": true
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints Null") {
    val v = V.nul

    val expected = """
    |"NULL": true
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints binary") {
    val v =
      V.b(ByteVector.fromValidBase64("dGhpcyB0ZXh0IGlzIGJhc2U2NC1lbmNvZGVk"))

    val expected = """
    |"B": "dGhpcyB0ZXh0IGlzIGJhc2U2NC1lbmNvZGVk"
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints lists") {
    val v = V.l(V.s("Cookies"), V.s("Coffee"), V.n(3.14159))

    val expected = """
    |"L": [ { "S": "Cookies" }, { "S": "Coffee" }, { "N": "3.14159" } ]
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints maps") {
    val v = V.m("Name" -> V.s("Joe"), "Age" -> V.n(35))

    val expected = """
    |"M": { "Age": { "N": "35" }, "Name": { "S": "Joe" } }
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints number sets") {
    val n = DynamoValue.Number
    val v = V.ns(NonEmptySet.of(n.of(42.2), n.of(-19), n.of(7.5), n.of(3.14)))

    val expected = """
    |"NS": [ "42.2", "-19", "7.5", "3.14" ]
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints string sets") {
    val v = V.ss(NonEmptySet.of("Giraffe", "Hippo", "Zebra"))

    val expected = """
    |"SS": [ "Giraffe", "Hippo", "Zebra" ]
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints binary sets") {
    val v = {
      val binaries =
        Set("U3Vubnk=", "UmFpbnk=", "U25vd3k=")
          .map(b => ByteVector.fromValidBase64(b))

      V.bs(NonEmptySet.unsafeFromSet(binaries))
    }

    val expected = """
    |"BS": [ "U3Vubnk=", "UmFpbnk=", "U25vd3k=" ]
    """.trim.stripMargin

    val s = v.print(100)

    assertEquals(s, expected)
  }

  test("pretty prints composite") {
    val v = V.m(
      "id" -> V.n(10),
      "food" -> V.ss(NonEmptySet.of("Rice", "Noodles")),
      "age" -> V.n(1),
      "isThatYou" -> V.bool(true),
      "files" -> V.l(
        V.m(
          "fileName" -> V.s("myfile.pdf"),
          "uri" -> V.s("https://mything.co.uk/123454")
        )
      ),
      "day" -> V.s("Tuesday"),
      "options" -> V.nul
    )

    val expected = """
    |"M": {
    |  "age": { "N": "1" },
    |  "day": { "S": "Tuesday" },
    |  "files": {
    |    "L": [
    |      {
    |        "M": {
    |          "fileName": {
    |            "S": "myfile.pdf"
    |          },
    |          "uri": {
    |            "S": "https://mything.co.uk/123454"
    |          }
    |        }
    |      }
    |    ]
    |  },
    |  "food": {
    |    "SS": [ "Rice", "Noodles" ]
    |  },
    |  "id": { "N": "10" },
    |  "isThatYou": { "BOOL": true },
    |  "options": { "NULL": true }
    |}
    """.trim.stripMargin

    val s = v.print(40)

    assertEquals(s, expected)
  }
}

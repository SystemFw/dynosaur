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

package dynosaur.path

import dynosaur.DynamoValue

class DynamoValuePathSuite extends munit.ScalaCheckSuite {
  import DynamoValuePath._

  case class Case(
      expression: String,
      source: DynamoValue,
      expectedResult: DynamoValue
  )

  test("toBoolean should return false for null") {
    assertEquals(toBoolean(DynamoValue.nul), false)
  }

  test("toBoolean should return false for empty string") {
    assertEquals(toBoolean(DynamoValue.s("")), false)
  }

  test("toBoolean should return false for 0") {
    assertEquals(toBoolean(DynamoValue.n(0)), false)
  }

  test("toBoolean should return false for false") {
    assertEquals(toBoolean(DynamoValue.bool(false)), false)
  }

  test("toBoolean should return true for true") {
    assertEquals(toBoolean(DynamoValue.bool(true)), true)
  }

  test("toBoolean should return true for notNull") {
    assertEquals(toBoolean(DynamoValue.s("foo")), true)
  }

  test("toBoolean should return true for 1") {
    assertEquals(toBoolean(DynamoValue.n(1)), true)
  }

  List(
    Case(
      expression = "$",
      source = DynamoValue.s("it is me"),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("it is me")
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$['foo']",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("it is me")
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$[2]",
      source = DynamoValue.l(
        DynamoValue.s("it isn't me"),
        DynamoValue.s("it isn't me"),
        DynamoValue.s("it is me"),
        DynamoValue.s("it isn't me")
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar['baz']",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.m("baz" -> DynamoValue.s("it is me"))
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$['foo']",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("it is me")
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar[2]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it is me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar[1].baz",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "baz" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.m(
              "baz" -> DynamoValue.s("it is me")
            ),
            DynamoValue.m(
              "baz" -> DynamoValue.s("it isn't me")
            )
          )
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$['foo']['bar'][2]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it is me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar[?($.foo)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.s("it is me")
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$[?(true || false)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("whatever"),
        "bar" -> DynamoValue.s("whatever")
      ),
      expectedResult = DynamoValue.m(
        "foo" -> DynamoValue.s("whatever"),
        "bar" -> DynamoValue.s("whatever")
      )
    ),
    Case(
      expression = "$[?(true && true)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("whatever"),
        "bar" -> DynamoValue.s("whatever")
      ),
      expectedResult = DynamoValue.m(
        "foo" -> DynamoValue.s("whatever"),
        "bar" -> DynamoValue.s("whatever")
      )
    ),
    Case(
      expression = "$[?(true && false)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.s("whatever"),
        "bar" -> DynamoValue.s("whatever")
      ),
      expectedResult = DynamoValue.nul
    ),
    Case(
      expression = "$.foo.bar[?(!$.foo)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.s("it is not me")
        )
      ),
      expectedResult = DynamoValue.nul
    ),
    Case(
      expression = "$.foo.bar[?(@.pickMe)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is me"),
              "pickMe" -> DynamoValue.s("whatever")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(true),
          "name" -> DynamoValue.s("it is me"),
          "pickMe" -> DynamoValue.s("whatever")
        )
      )
    ),
    Case(
      expression = "$.foo.bar[?(!@.enabled)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is not me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.s("it is me"),
            DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(false),
          "name" -> DynamoValue.s("it is me")
        ),
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(false),
          "name" -> DynamoValue.s("it is me")
        ),
        DynamoValue.s("it is me"),
        DynamoValue.s("it is me")
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.enabled && !@.deleted)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it is not me"),
              "deleted" -> DynamoValue.bool(false)
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is me"),
              "deleted" -> DynamoValue.bool(false)
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is not me"),
              "deleted" -> DynamoValue.bool(true)
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(true),
          "name" -> DynamoValue.s("it is me"),
          "deleted" -> DynamoValue.bool(false)
        )
      )
    ),
    Case(
      expression = "$.foo.bar[?(!@.enabled == true)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is not me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.s("it is me"),
            DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(false),
          "name" -> DynamoValue.s("it is me")
        ),
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(false),
          "name" -> DynamoValue.s("it is me")
        ),
        DynamoValue.s("it is me"),
        DynamoValue.s("it is me")
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.enabled == true)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "enabled" -> DynamoValue.bool(true),
          "name" -> DynamoValue.s("it is me")
        )
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.pickMe)].name",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is me"),
              "pickMe" -> DynamoValue.s("whatever")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.s("it is me")
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.enabled == true)].name",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(true),
              "name" -> DynamoValue.s("it is me")
            ),
            DynamoValue.m(
              "enabled" -> DynamoValue.bool(false),
              "name" -> DynamoValue.s("it isn't me")
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.s("it is me")
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.enabled == true)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.m(
            "enabled" -> DynamoValue.bool(true),
            "name" -> DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.m(
        "enabled" -> DynamoValue.bool(true),
        "name" -> DynamoValue.s("it is me")
      )
    ),
    Case(
      expression = "$.foo.bar[?(@.enabled == true)].name",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.m(
            "enabled" -> DynamoValue.bool(true),
            "name" -> DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar[?($.foo)].name",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.m(
            "enabled" -> DynamoValue.bool(true),
            "name" -> DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.s("it is me")
    ),
    Case(
      expression = "$.foo.bar[?($.bar)].name",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.m(
            "enabled" -> DynamoValue.bool(true),
            "name" -> DynamoValue.s("it is me")
          )
        )
      ),
      expectedResult = DynamoValue.nul
    ),
    Case(
      expression = "$.foo.bar[?(@.tags empty true)]",
      source = DynamoValue.m(
        "foo" -> DynamoValue.m(
          "bar" -> DynamoValue.l(
            DynamoValue.m(
              "name" -> DynamoValue.s("it isn't me"),
              "tags" -> DynamoValue.l(
                DynamoValue.s("one")
              )
            ),
            DynamoValue.m(
              "name" -> DynamoValue.s("it is me"),
              "tags" -> DynamoValue.l()
            ),
            DynamoValue.m(
              "name" -> DynamoValue.s("it isn't me"),
              "tags" -> DynamoValue.l(
                DynamoValue.s("one"),
                DynamoValue.s("two")
              )
            ),
            DynamoValue.s("it isn't me"),
            DynamoValue.s("it isn't me")
          )
        )
      ),
      expectedResult = DynamoValue.l(
        DynamoValue.m(
          "name" -> DynamoValue.s("it is me"),
          "tags" -> DynamoValue.l()
        )
      )
    )
  ).map { case Case(expression, source, expected) =>
    test(s"""should support "${expression}"""") {

      val result = DynamoValuePath.matchDynamoValue(source, expression)

      assertEquals(
        clue(result),
        Right(expected),
        s"Cannot parse ${expression}"
      )
    }
  }
}

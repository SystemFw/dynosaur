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

package com.ovoenergy.comms.aws
package dynamodb

import common._
import model._

class ModelSpec extends UnitSpec {

  "ExpressionPlaceholder.apply" when {
    "The expression is prefixed with ':'" should {
      "compile" in {
        ExpressionPlaceholder(":foo")
      }
    }

    "The expression is not prefixed with ':'" should {
      "not compile" in {
        val universe: scala.reflect.runtime.universe.type =
          scala.reflect.runtime.universe
        import universe._

        // TODO Check that it does not compile

        q"""ExpressionPlaceholder("foo")"""
      }
    }
  }

  "ExpressionAlias.apply" when {
    "The expression is prefixed with '#'" should {
      "compile" in {
        ExpressionAlias("#foo")
      }
    }

    "The expression is not prefixed with '#'" should {
      "not compile" in {
        val universe: scala.reflect.runtime.universe.type =
          scala.reflect.runtime.universe
        import universe._

        // TODO Check that it does not compile

        q"""ExpressionAlias("foo")"""
      }
    }
  }

}

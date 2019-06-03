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

import dynosaur.macros.PrismDerive

class PrismDerivationSpec extends UnitSpec {
  sealed trait A
  case object B extends A
  case object C extends A

  "Derived prisms" should {
    "classify data correctly" in {
      pendingUntilFixed {
        info("Bare derivation seems to work, but derived Prism doesn't ")
        val (aToOptB, btoA @ _) = PrismDerive[A, B.type]
        val (aToOptC, cToA @ _) = PrismDerive[A, C.type]
        val pB = Prism.derive[A, B.type]
        val pC = Prism.derive[A, C.type]

        val b: A = B

        def derive2[T, S <: T]: T => Option[S] =
          PrismDerive[T, S]._1

        val spC = derive2[A, C.type]

        println(s"bare ${aToOptC(b)}")
        println(s"in prism ${pC.tryGet(b)}")
        println(s"simul ${spC.apply(b)}")

        assert(aToOptB(b) === Some(B))
        assert(aToOptC(b) === None)
        assert(pB.tryGet(b) === Some(B))
        assert(pC.tryGet(b) === None)
        assert(spC.apply(b) === None)
        ()
      }
    }
  }
}

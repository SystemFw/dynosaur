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

import macros.PrismDerive

case class Prism[A, B](tryGet: A => Option[B], inject: B => A)
object Prism {
  def fromPartial[A, B](tryGet: PartialFunction[A, B])(inject: B => A) =
    Prism(tryGet.lift, inject)

  def derive[T, S <: T]: Prism[T, S] = {
    // it's actually a macro with a TypeTag, so this is a false positive
    val p = PrismDerive[T, S @unchecked]
    Prism(p._1, p._2)
  }
}

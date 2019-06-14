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

import scala.reflect.macros.blackbox

case class Prism[A, B](tryGet: A => Option[B], inject: B => A)
object Prism {
  def fromPartial[A, B](tryGet: PartialFunction[A, B])(inject: B => A) =
    Prism(tryGet.lift, inject)

  implicit def derive[T, S <: T]: Prism[T, S] = macro DerivedPrism.impl[T, S]
}

private class DerivedPrism(val c: blackbox.Context) {
  def impl[T: c.WeakTypeTag, S: c.WeakTypeTag]: c.Expr[Prism[T, S]] = {
    import c.universe._

    val (tTpe, sTpe) = (weakTypeOf[T], weakTypeOf[S])
    c.Expr[Prism[T, S]](q"""
       import dynosaur.codec.Prism

       val tryGet: $tTpe => Option[$sTpe] = t => if (t.isInstanceOf[$sTpe]) Some(t.asInstanceOf[$sTpe]) else None
       val inject: $sTpe => $tTpe = _.asInstanceOf[$tTpe]
       Prism(tryGet, inject)
    """)
  }
}

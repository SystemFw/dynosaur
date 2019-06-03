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

/*
 * Implementation for the prism derivation is taken from the `monocle` library, with only minor changes. See:
 *
 * https://github.com/julien-truffaut/Monocle/blob/master/macro/shared/src/main/scala/monocle/macros/GenPrism.scala
 *
 * This code is copyright Julien Truffaut, Omer van Kloeten, David Barri, Kenji Yoshida and other contributors,
 * and is licensed using MIT, see license file at:
 *
 * https://github.com/julien-truffaut/Monocle/blob/master/LICENSE
 *
 */

package dynosaur
package macros

import scala.reflect.macros.blackbox

case class PrismDerive[A, B](tryGet: A => Option[B], inject: B => A)
object PrismDerive {
  implicit def create[T, S <: T]: PrismDerive[T, S] = macro PrismDeriveImpl.deriveImpl[T, S]
}

private class PrismDeriveImpl(val c: blackbox.Context) {
  def deriveImpl[T: c.WeakTypeTag, S: c.WeakTypeTag]: c.Expr[PrismDerive[T, S]] = {
    import c.universe._

    val (tTpe, sTpe) = (weakTypeOf[T], weakTypeOf[S])
    c.Expr[PrismDerive[T, S]](q"""
       import dynosaur.macros.PrismDerive

       val tryGet: $tTpe => Option[$sTpe] = t => if (t.isInstanceOf[$sTpe]) Some(t.asInstanceOf[$sTpe]) else None
       val inject: $sTpe => $tTpe = _.asInstanceOf[$tTpe]
       PrismDerive(tryGet, inject)
    """)
  }
}

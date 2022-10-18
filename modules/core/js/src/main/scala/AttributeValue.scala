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

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

@js.native
trait AttributeValue extends js.Object {
  def S: js.UndefOr[String] = js.native
  def N: js.UndefOr[String] = js.native
  def B: js.UndefOr[Uint8Array] = js.native
  def SS: js.UndefOr[js.Array[String]] = js.native
  def NS: js.UndefOr[js.Array[String]] = js.native
  def BS: js.UndefOr[js.Array[Uint8Array]] = js.native
  def M: js.UndefOr[js.Dictionary[AttributeValue]] = js.native
  def L: js.UndefOr[js.Array[AttributeValue]] = js.native
  def NULL: js.UndefOr[Boolean] = js.native
  def BOOL: js.UndefOr[Boolean] = js.native
  def $unknown: js.UndefOr[js.Tuple2[String, js.Any]] = js.native
}

object AttributeValue {
  val NULL: AttributeValue =
    js.Dynamic.literal(NULL = true).asInstanceOf[AttributeValue]
  def BOOL(value: Boolean): AttributeValue =
    js.Dynamic.literal(BOOL = value).asInstanceOf[AttributeValue]
  def S(value: String): AttributeValue =
    js.Dynamic.literal(S = value).asInstanceOf[AttributeValue]
  def N(value: String): AttributeValue =
    js.Dynamic.literal(N = value).asInstanceOf[AttributeValue]
  def B(value: Uint8Array): AttributeValue =
    js.Dynamic.literal(B = value).asInstanceOf[AttributeValue]
  def M(value: js.Dictionary[AttributeValue]): AttributeValue =
    js.Dynamic.literal(M = value).asInstanceOf[AttributeValue]
  def L(value: js.Array[AttributeValue]): AttributeValue =
    js.Dynamic.literal(L = value).asInstanceOf[AttributeValue]
  def SS(value: js.Array[String]): AttributeValue =
    js.Dynamic.literal(SS = value).asInstanceOf[AttributeValue]
  def NS(value: js.Array[String]): AttributeValue =
    js.Dynamic.literal(NS = value).asInstanceOf[AttributeValue]
  def BS(value: js.Array[Uint8Array]): AttributeValue =
    js.Dynamic.literal(BS = value).asInstanceOf[AttributeValue]
}

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

import dynosaur.Schema

/** caching example, only encoding so far, depends on printlns in encoding */
object Ex {
  case class Foo(a: Int)
  val s = Schema.record[Foo](field => field("a", _.a).map(Foo.apply))
  val a = Foo(3)

  // run a few times
  def test = s.write(a)


  // traversal of records cannot be cached, because the computations are monadic
  // so the traversal cannot be done all in advance.
  // To make that work I would need to go back to FreeAp, which in turn prevents
  // using `for` for big case classes (fixed in scala 3)
  // In scala 2, unless you use kittens, the code below is the best you can do

  // case class Bar(a: Int, b: Int, c: Int, d: Int)

  // val a = (1.some, 2.some).mapN((x, y) => Bar(a = x, b = y, _, _))
  // val b = (a, 3.some, 4.some).mapN((p, a, b) => p(a, b))

}

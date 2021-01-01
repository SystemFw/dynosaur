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
  val s = Schema.oneOf[Foo] { alt =>
    alt(Schema.record[Foo](field => field("a", _.a).map(Foo.apply)))
  }
  val a = Foo(3)

  // run a few times
  def test = s.write(a)

  def read = {
    s.write(a).flatMap(s.read)
  }

  // the static structures (Chain, FreeAp) get recompiled each time, even though the schema is a val
  // scala> Ex.test
  // building Sum(Chain(dynosaur.Schema$AltBuilder$$anon$4@7f88013f))
  // compiling sums
  // traversing sum structure
  // building Record(FreeApplicative(...))
  // compiling records
  // traversing record structure
  // building Isos(dynosaur.Schema$$anon$3@70af4b56)
  // building Num
  // val res1: Either[dynosaur.Schema.WriteError,dynosaur.DynamoValue] = Right("M": { "a": { "N": "3" } })

  // scala> Ex.test
  // compiling sums
  // traversing sum structure
  // compiling records
  // traversing record structure
  // val res2: Either[dynosaur.Schema.WriteError,dynosaur.DynamoValue] = Right("M": { "a": { "N": "3" } })

  // scala> Ex.read
  // compiling sums
  // traversing sum structure
  // compiling records
  // traversing record structure
  // building Sum(Chain(dynosaur.Schema$AltBuilder$$anon$4@7f88013f)) - decoder
  // compiling sums - decoder
  // traversing sum structure - decoder
  // building Record(FreeApplicative(...)) - decoder
  // compiling records - decoder
  // traversing record structure
  // building Isos(dynosaur.Schema$$anon$3@70af4b56) - decoder
  // building Num - decoder
  // val res3: scala.util.Either[dynosaur.Schema.DynosaurError,Ex.Foo] = Right(Foo(3))

  // scala> Ex.read
  // compiling sums
  // traversing sum structure
  // compiling records
  // traversing record structure
  // compiling sums - decoder
  // traversing sum structure - decoder
  // compiling records - decoder
  // traversing record structure

  // val res4: scala.util.Either[dynosaur.Schema.DynosaurError,Ex.Foo] = Right(Foo(3))
  // scala> Ex.test
  // building Sum(Chain(dynosaur.Schema$AltBuilder$$anon$4@7f88013f))
  // compiling sums
  // traversing sum structure
  // building Record(FreeApplicative(...))
  // compiling records
  // traversing record structure
  // building Isos(dynosaur.Schema$$anon$3@70af4b56)
  // building Num
  // val res1: Either[dynosaur.Schema.WriteError,dynosaur.DynamoValue] = Right("M": { "a": { "N": "3" } })

}

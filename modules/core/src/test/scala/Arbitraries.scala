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

import scodec.bits._

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._

trait Generators {
  def genNonEmptyString(genMaxSize: Gen[Int] = const(6)): Gen[String] =
    for {
      maxSize <- genMaxSize
      size <- choose(1, maxSize)
      xs <- listOfN(size, alphaChar)
    } yield xs.mkString

  val genNonEmptyString: Gen[String] = genNonEmptyString()

  def genNumberAsString(
      genMin: Gen[Double] = const(Double.MinValue),
      genMax: Gen[Double] = const(Double.MaxValue)
  ) =
    for {
      min <- genMin
      max <- genMax
      value <- choose(min, max)
    } yield Value.Number(value.toString) // TODO rename

  val genNumberAsString: Gen[Value.Number] = genNumberAsString()

  def genByteVector(genSize: Gen[Int] = choose(0, 6)) =
    for {
      size <- genSize
      xs <- listOfN(size, arbitrary[Byte])
    } yield ByteVector(xs)

  def genAttributeName =
    for {
      value <- genNonEmptyString
    } yield value

  def genValueB(genByteVectorSize: Gen[Int] = choose(0, 6)) =
    for {
      value <- genByteVector(genByteVectorSize)
    } yield Value.b(value)

  lazy val genValueBool = for {
    value <- arbitrary[Boolean]
  } yield Value.bool(value)

  def genValueBs(
      genSize: Gen[Int] = choose(1, 6),
      genByteVectorSize: Gen[Int] = choose(0, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, ByteVector](
        size,
        genByteVector(genByteVectorSize)
      )
    } yield Value.bs(NonEmptySet.unsafeFromSet(values))

  def genValueS(genStringMaxSize: Gen[Int] = choose(1, 6)) =
    for {
      value <- genNonEmptyString(genStringMaxSize)
    } yield Value.s(value)

  def genValueSs(
      genSize: Gen[Int] = choose(1, 6),
      genStringMaxSize: Gen[Int] = choose(1, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](
        size,
        genNonEmptyString(genStringMaxSize)
      )
    } yield Value.ss(NonEmptySet.unsafeFromSet(values))

  def genValueN() =
    for {
      value <- genNumberAsString
    } yield Value.n(value)

  def genValueNs(genSize: Gen[Int] = choose(1, 6)) =
    for {
      size <- genSize
      values <- containerOfN[Set, Value.Number](size, genNumberAsString)
    } yield Value.ns(NonEmptySet.unsafeFromSet(values))

  def genValueL(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[Value] =
    for {
      size <- genSize
      values <- containerOfN[List, Value](
        size,
        genValue(maxDeep.map(_ - 1))
      )
    } yield Value.l(values)

  def genValueM(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[Value] =
    for {
      size <- genSize
      values <- mapOfN(
        size,
        zip(genAttributeName, genValue(maxDeep.map(_ - 1)))
      )
    } yield Value.m(values)

  val genValueNul = const(Value.nul)

  // TODO rename all this
  def genValue(genMaxDeep: Gen[Int] = const(3)) =
    for {
      maxDeep <- genMaxDeep
      attributeValue <-
        if (maxDeep > 0) {
          oneOf(
            genValueNul,
            genValueBool,
            genValueS(),
            genValueSs(),
            genValueN(),
            genValueNs(),
            genValueB(),
            genValueBs(),
            genValueL(genMaxDeep),
            genValueM(genMaxDeep)
          )
        } else {
          oneOf(
            genValueNul,
            genValueBool,
            genValueS(),
            genValueSs(),
            genValueN(),
            genValueNs(),
            genValueB(),
            genValueBs()
          )
        }
    } yield attributeValue

}

// TODO review this whole file
// TODO generate AttributeValue to test roundtrip, if that doesn't end up duplicating
trait Arbitraries extends Generators {
  implicit lazy val arbValue: Arbitrary[Value] = Arbitrary(
    genValue()
  )
}

object Arbitraries extends Arbitraries

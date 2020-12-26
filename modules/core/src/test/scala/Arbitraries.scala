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
    } yield value.toString

  val genNumberAsString: Gen[String] = genNumberAsString()

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
    } yield Value.B(value)

  lazy val genValueBOOL = for {
    value <- arbitrary[Boolean]
  } yield Value.Bool(value)

  def genValueBS(
      genSize: Gen[Int] = choose(1, 6),
      genByteVectorSize: Gen[Int] = choose(0, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, ByteVector](
        size,
        genByteVector(genByteVectorSize)
      )
    } yield Value.BS(NonEmptySet.unsafeFromSet(values))

  def genValueS(genStringMaxSize: Gen[Int] = choose(1, 6)) =
    for {
      value <- genNonEmptyString(genStringMaxSize)
    } yield Value.S(value)

  def genValueSS(
      genSize: Gen[Int] = choose(1, 6),
      genStringMaxSize: Gen[Int] = choose(1, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](
        size,
        genNonEmptyString(genStringMaxSize)
      )
    } yield Value.SS(NonEmptySet.unsafeFromSet(values))

  def genValueN() =
    for {
      value <- genNumberAsString
    } yield Value.N(value)

  def genValueNS(genSize: Gen[Int] = choose(1, 6)) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](size, genNumberAsString)
    } yield Value.NS(NonEmptySet.unsafeFromSet(values))

  def genValueL(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[Value.L] =
    for {
      size <- genSize
      values <- containerOfN[Vector, Value](
        size,
        genValue(maxDeep.map(_ - 1))
      )
    } yield Value.L(values)

  def genValueM(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[Value.M] =
    for {
      size <- genSize
      values <- mapOfN(
        size,
        zip(genAttributeName, genValue(maxDeep.map(_ - 1)))
      )
    } yield Value.M(values)

  val genValueNULL = const(Value.Nul)

  def genValue(genMaxDeep: Gen[Int] = const(3)) =
    for {
      maxDeep <- genMaxDeep
      attributeValue <-
        if (maxDeep > 0) {
          oneOf(
            genValueNULL,
            genValueBOOL,
            genValueS(),
            genValueSS(),
            genValueN(),
            genValueNS(),
            genValueB(),
            genValueBS(),
            genValueL(genMaxDeep),
            genValueM(genMaxDeep)
          )
        } else {
          oneOf(
            genValueNULL,
            genValueBOOL,
            genValueS(),
            genValueSS(),
            genValueN(),
            genValueNS(),
            genValueB(),
            genValueBS()
          )
        }
    } yield attributeValue
}

object Generators extends Generators

trait Arbitraries {
  import Generators._

  // implicit lazy val arbAttributeName: Arbitrary[AttributeName] =
  //   Arbitrary(genAttributeName)

  implicit lazy val arbValueNULL: Arbitrary[Value.Nul.type] =
    Arbitrary(genValueNULL)

  implicit lazy val arbValueBool: Arbitrary[Value.Bool] =
    Arbitrary(genValueBOOL)

  implicit lazy val arbValueS: Arbitrary[Value.S] = Arbitrary(
    genValueS()
  )

  implicit lazy val arbValueSS: Arbitrary[Value.SS] =
    Arbitrary(genValueSS())

  implicit lazy val arbValueN: Arbitrary[Value.N] = Arbitrary(
    genValueN()
  )

  implicit lazy val arbValueNS: Arbitrary[Value.NS] =
    Arbitrary(genValueNS())

  implicit lazy val arbValueB: Arbitrary[Value.B] = Arbitrary(
    genValueB()
  )

  implicit lazy val arbValueBS: Arbitrary[Value.BS] =
    Arbitrary(genValueBS())

  implicit def arbValueL: Arbitrary[Value.L] =
    Arbitrary(genValueL())

  implicit def arbValueM: Arbitrary[Value.M] =
    Arbitrary(genValueM())

  implicit lazy val arbValue: Arbitrary[Value] = Arbitrary(
    genValue()
  )
}

object Arbitraries extends Arbitraries

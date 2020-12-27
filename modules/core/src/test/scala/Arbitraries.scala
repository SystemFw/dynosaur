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
    } yield DynamoValue.Number(value.toString) // TODO rename

  val genNumberAsString: Gen[DynamoValue.Number] = genNumberAsString()

  def genByteVector(genSize: Gen[Int] = choose(0, 6)) =
    for {
      size <- genSize
      xs <- listOfN(size, arbitrary[Byte])
    } yield ByteVector(xs)

  def genAttributeName =
    for {
      value <- genNonEmptyString
    } yield value

  def genDynamoValueB(genByteVectorSize: Gen[Int] = choose(0, 6)) =
    for {
      value <- genByteVector(genByteVectorSize)
    } yield DynamoValue.b(value)

  lazy val genDynamoValueBool = for {
    value <- arbitrary[Boolean]
  } yield DynamoValue.bool(value)

  def genDynamoValueBs(
      genSize: Gen[Int] = choose(1, 6),
      genByteVectorSize: Gen[Int] = choose(0, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, ByteVector](
        size,
        genByteVector(genByteVectorSize)
      )
    } yield DynamoValue.bs(NonEmptySet.unsafeFromSet(values))

  def genDynamoValueS(genStringMaxSize: Gen[Int] = choose(1, 6)) =
    for {
      value <- genNonEmptyString(genStringMaxSize)
    } yield DynamoValue.s(value)

  def genDynamoValueSs(
      genSize: Gen[Int] = choose(1, 6),
      genStringMaxSize: Gen[Int] = choose(1, 6)
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](
        size,
        genNonEmptyString(genStringMaxSize)
      )
    } yield DynamoValue.ss(NonEmptySet.unsafeFromSet(values))

  def genDynamoValueN() =
    for {
      value <- genNumberAsString
    } yield DynamoValue.n(value)

  def genDynamoValueNs(genSize: Gen[Int] = choose(1, 6)) =
    for {
      size <- genSize
      values <- containerOfN[Set, DynamoValue.Number](size, genNumberAsString)
    } yield DynamoValue.ns(NonEmptySet.unsafeFromSet(values))

  def genDynamoValueL(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[DynamoValue] =
    for {
      size <- genSize
      values <- containerOfN[List, DynamoValue](
        size,
        genDynamoValue(maxDeep.map(_ - 1))
      )
    } yield DynamoValue.l(values)

  def genDynamoValueM(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)
  ): Gen[DynamoValue] =
    for {
      size <- genSize
      values <- mapOfN(
        size,
        zip(genAttributeName, genDynamoValue(maxDeep.map(_ - 1)))
      )
    } yield DynamoValue.m(values)

  val genDynamoValueNul = const(DynamoValue.nul)

  // TODO rename all this
  def genDynamoValue(genMaxDeep: Gen[Int] = const(3)) =
    for {
      maxDeep <- genMaxDeep
      attributeDynamoValue <-
        if (maxDeep > 0) {
          oneOf(
            genDynamoValueNul,
            genDynamoValueBool,
            genDynamoValueS(),
            genDynamoValueSs(),
            genDynamoValueN(),
            genDynamoValueNs(),
            genDynamoValueB(),
            genDynamoValueBs(),
            genDynamoValueL(genMaxDeep),
            genDynamoValueM(genMaxDeep)
          )
        } else {
          oneOf(
            genDynamoValueNul,
            genDynamoValueBool,
            genDynamoValueS(),
            genDynamoValueSs(),
            genDynamoValueN(),
            genDynamoValueNs(),
            genDynamoValueB(),
            genDynamoValueBs()
          )
        }
    } yield attributeDynamoValue

}

// TODO review this whole file
// TODO generate AttributeDynamoValue to test roundtrip, if that doesn't end up duplicating
trait Arbitraries extends Generators {
  implicit lazy val arbDynamoValue: Arbitrary[DynamoValue] = Arbitrary(
    genDynamoValue()
  )
}

object Arbitraries extends Arbitraries

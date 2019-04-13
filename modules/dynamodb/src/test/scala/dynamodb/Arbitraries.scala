/*
 * Copyright 2018 OVO Energy
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

package com.ovoenergy.comms.aws
package dynamodb

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._

import scodec.bits._
import model._

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
      genMax: Gen[Double] = const(Double.MaxValue),
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
    } yield AttributeName(value)

  def genAttributeValueB(genByteVectorSize: Gen[Int] = choose(0, 6)) =
    for {
      value <- genByteVector(genByteVectorSize)
    } yield AttributeValue.B(value)

  lazy val genAttributeValueBOOL = for {
    value <- arbitrary[Boolean]
  } yield AttributeValue.BOOL(value)

  def genAttributeValueBS(
      genSize: Gen[Int] = choose(0, 6),
      genByteVectorSize: Gen[Int] = choose(0, 6)) =
    for {
      size <- genSize
      values <- containerOfN[Set, ByteVector](
        size,
        genByteVector(genByteVectorSize))
    } yield AttributeValue.BS(values)

  def genAttributeValueS(genStringMaxSize: Gen[Int] = choose(1, 6)) =
    for {
      value <- genNonEmptyString(genStringMaxSize)
    } yield AttributeValue.S(value)

  def genAttributeValueSS(
      genSize: Gen[Int] = choose(0, 6),
      genStringMaxSize: Gen[Int] = choose(1, 6),
  ) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](
        size,
        genNonEmptyString(genStringMaxSize))
    } yield AttributeValue.SS(values)

  def genAttributeValueN() =
    for {
      value <- genNumberAsString
    } yield AttributeValue.N(value)

  def genAttributeValueNS(genSize: Gen[Int] = choose(0, 6)) =
    for {
      size <- genSize
      values <- containerOfN[Set, String](size, genNumberAsString)
    } yield AttributeValue.NS(values)

  def genAttributeValueL(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)): Gen[AttributeValue.L] =
    for {
      size <- genSize
      values <- listOfN(size, genAttributeValue(maxDeep.map(_ - 1)))
    } yield AttributeValue.L(values)

  def genAttributeValueM(
      maxDeep: Gen[Int] = const(3),
      genSize: Gen[Int] = choose(0, 6)): Gen[AttributeValue.M] =
    for {
      size <- genSize
      values <- mapOfN(
        size,
        zip(genAttributeName, genAttributeValue(maxDeep.map(_ - 1))))
    } yield AttributeValue.M(values)

  val genAttributeValueNULL = const(AttributeValue.NULL)

  def genAttributeValue(genMaxDeep: Gen[Int] = const(3)) =
    for {
      maxDeep <- genMaxDeep
      attributeValue <- if (maxDeep > 0) {
        oneOf(
          genAttributeValueNULL,
          genAttributeValueBOOL,
          genAttributeValueS(),
          genAttributeValueSS(),
          genAttributeValueN(),
          genAttributeValueNS(),
          genAttributeValueB(),
          genAttributeValueBS(),
          genAttributeValueL(genMaxDeep),
          genAttributeValueM(genMaxDeep),
        )
      } else {
        oneOf(
          genAttributeValueNULL,
          genAttributeValueBOOL,
          genAttributeValueS(),
          genAttributeValueSS(),
          genAttributeValueN(),
          genAttributeValueNS(),
          genAttributeValueB(),
          genAttributeValueBS(),
        )
      }
    } yield attributeValue
}

object Generators extends Generators

trait Arbitraries {
  import Generators._

  implicit lazy val arbAttributeName: Arbitrary[AttributeName] =
    Arbitrary(genAttributeName)

  implicit lazy val arbAttributeValueNULL: Arbitrary[AttributeValue.NULL.type] =
    Arbitrary(genAttributeValueNULL)

  implicit lazy val arbAttributeValueBOOL: Arbitrary[AttributeValue.BOOL] =
    Arbitrary(genAttributeValueBOOL)

  implicit lazy val arbAttributeValueS: Arbitrary[AttributeValue.S] = Arbitrary(
    genAttributeValueS())

  implicit lazy val arbAttributeValueSS: Arbitrary[AttributeValue.SS] =
    Arbitrary(genAttributeValueSS())

  implicit lazy val arbAttributeValueN: Arbitrary[AttributeValue.N] = Arbitrary(
    genAttributeValueN())

  implicit lazy val arbAttributeValueNS: Arbitrary[AttributeValue.NS] =
    Arbitrary(genAttributeValueNS())

  implicit lazy val arbAttributeValueB: Arbitrary[AttributeValue.B] = Arbitrary(
    genAttributeValueB())

  implicit lazy val arbAttributeValueBS: Arbitrary[AttributeValue.BS] =
    Arbitrary(genAttributeValueBS())

  implicit def arbAttributeValueL: Arbitrary[AttributeValue.L] =
    Arbitrary(genAttributeValueL())

  implicit def arbAttributeValueM: Arbitrary[AttributeValue.M] =
    Arbitrary(genAttributeValueM())

  implicit lazy val arbAttributeValue: Arbitrary[AttributeValue] = Arbitrary(
    genAttributeValue()
  )

  implicit lazy val arbTableName: Arbitrary[TableName] = Arbitrary(
    genNonEmptyString.map(TableName(_))
  )

  implicit lazy val arbReturnValues: Arbitrary[ReturnValues] = Arbitrary(
    oneOf(
      ReturnValues.None,
      ReturnValues.AllOld,
      ReturnValues.AllNew,
      ReturnValues.UpdatedOld,
      ReturnValues.UpdatedNew
    )
  )

  implicit lazy val arbPutItemRequest: Arbitrary[PutItemRequest] = Arbitrary(
    for {
      tableName <- arbitrary[TableName]
      item <- arbitrary[AttributeValue.M]
      returnValues <- arbitrary[ReturnValues]
    } yield
      PutItemRequest(
        tableName = tableName,
        item = item,
        returnValues = returnValues
      )
  )

  implicit lazy val arbPutItemResponse: Arbitrary[PutItemResponse] = Arbitrary(
    for {
      attributes <- arbitrary[Option[AttributeValue.M]]
    } yield PutItemResponse(attributes)
  )

  implicit lazy val arbGetItemRequest: Arbitrary[GetItemRequest] = Arbitrary(
    for {
      tableName <- arbitrary[TableName]
      key <- arbitrary[AttributeValue.M]
    } yield GetItemRequest(tableName, key)
  )

  implicit lazy val arbGetItemResponse: Arbitrary[GetItemResponse] = Arbitrary(
    for {
      item <- arbitrary[Option[AttributeValue.M]]
    } yield GetItemResponse(item)
  )

}

object Arbitraries extends Arbitraries

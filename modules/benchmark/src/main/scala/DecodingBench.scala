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

import cats.syntax.all._

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Mode,
  OutputTimeUnit
}
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Scope
import cats.data.NonEmptyList

sealed trait Dynosaur
object Dynosaur {
  case class Parasaurolophus(name: String, age: Int, songs: Int)
      extends Dynosaur
  case class TyrannosaurusRex(name: String, age: Int, victims: Int)
      extends Dynosaur
  case class Allosaurus(name: String, age: Int, attacks: Int) extends Dynosaur
}

import Dynosaur._

object DecodingBench {

  val tyrannosaurusRexDv = DynamoValue.m(
    "name" -> DynamoValue.s("Foolio"),
    "age" -> DynamoValue.n(20000000),
    "victims" -> DynamoValue.n(9)
  )

  val parasaurolophusDv = DynamoValue.m(
    "name" -> DynamoValue.s("Cantolio"),
    "age" -> DynamoValue.n(25000000),
    "songs" -> DynamoValue.n(9)
  )

  val allosaurusDv = DynamoValue.m(
    "name" -> DynamoValue.s("Cantolio"),
    "age" -> DynamoValue.n(25000000),
    "attacks" -> DynamoValue.n(99)
  )

  val tyrannosaurusRexWithTagDv = DynamoValue.m(
    "tyrannosaurus-rex" -> tyrannosaurusRexDv
  )

  val allosaurusWithTagDv = DynamoValue.m(
    "allosaurus" -> allosaurusDv
  )

  val parasaurolophusWithTagDv = DynamoValue.m(
    "parasaurolophus" -> parasaurolophusDv
  )

  val allosauruses = DynamoValue.l(
    (0 until 10)
      .map(_ => allosaurusDv)
      .toList
  )

  val dynosaursWithTag = DynamoValue.l(
    tyrannosaurusRexWithTagDv,
    allosaurusWithTagDv,
    parasaurolophusWithTagDv
  )

  val dynosaurDvWithDiscriminator = DynamoValue.m(
    "name" -> DynamoValue.s("Foolio"),
    "age" -> DynamoValue.n(20000000),
    "victims" -> DynamoValue.n(9),
    "kind" -> DynamoValue.s("tyrannosaurus-rex")
  )

  val schermaForParasaurolophus: Schema[Parasaurolophus] =
    Schema.record[Parasaurolophus] { fields =>
      (
        fields("name", _.name),
        fields("age", _.age),
        fields("songs", _.songs)
      ).mapN(Parasaurolophus.apply)
    }

  def defSchermaForTyrannosaurusRex: Schema[TyrannosaurusRex] =
    Schema.record[TyrannosaurusRex] { fields =>
      (
        fields("name", _.name),
        fields("age", _.age),
        fields("victims", _.victims)
      ).mapN(TyrannosaurusRex.apply)
    }

  val schermaForTyrannosaurusRex: Schema[TyrannosaurusRex] =
    defSchermaForTyrannosaurusRex

  implicit def implicitSchermaForTyrannosaurusRex: Schema[TyrannosaurusRex] =
    defSchermaForTyrannosaurusRex

  val schermaForAllosaurus: Schema[Allosaurus] = Schema.record[Allosaurus] {
    fields =>
      (
        fields("name", _.name),
        fields("age", _.age),
        fields("attacks", _.attacks)
      ).mapN(Allosaurus.apply)
  }

  def defSchermaForAllosaurus: Schema[Allosaurus] = Schema.record[Allosaurus] {
    fields =>
      (
        fields("name", _.name),
        fields("age", _.age),
        fields("attacks", _.attacks)
      ).mapN(Allosaurus.apply)
  }

  implicit lazy val implicitSchermaForAllosaurus: Schema[Allosaurus] =
    defSchermaForAllosaurus

  val schermaForParasaurolophusWithDiscriminator: Schema[Parasaurolophus] =
    Schema.record[Parasaurolophus] { fields =>
      fields.const("kind", "parasaurolophus") *>
        (
          fields("name", _.name),
          fields("age", _.age),
          fields("songs", _.songs)
        ).mapN(Parasaurolophus.apply)
    }

  val schermaForTyrannosaurusRexWithDiscriminator: Schema[TyrannosaurusRex] =
    Schema.record[TyrannosaurusRex] { fields =>
      fields.const("kind", "tyrannosaurus-rex") *>
        (
          fields("name", _.name),
          fields("age", _.age),
          fields("victims", _.victims)
        ).mapN(TyrannosaurusRex.apply)
    }

  val schermaForAllosaurusWithDiscriminator: Schema[Allosaurus] =
    Schema.record[Allosaurus] { fields =>
      fields.const("kind", "allosaurus") *>
        (
          fields("name", _.name),
          fields("age", _.age),
          fields("attacks", _.attacks)
        ).mapN(Allosaurus.apply)
    }

  val schemaForDynosaurWithTag: Schema[Dynosaur] = Schema.oneOf { alt =>
    NonEmptyList
      .of(
        alt(schermaForParasaurolophus.tag("parasaurolophus")),
        alt(schermaForTyrannosaurusRex.tag("tyrannosaurus-rex")),
        alt(schermaForAllosaurus.tag("allosaurus"))
      )
      .reduceLeft(_ |+| _)
  }

  val schemaForDynosaurWithDiscriminator: Schema[Dynosaur] = Schema.oneOf {
    alt =>
      NonEmptyList
        .of(
          alt(schermaForParasaurolophusWithDiscriminator),
          alt(schermaForTyrannosaurusRexWithDiscriminator),
          alt(schermaForAllosaurusWithDiscriminator)
        )
        .reduceLeft(_ |+| _)
  }

  val schermaForAllosauruses: Schema[Seq[Allosaurus]] =
    Schema.seq(schermaForAllosaurus)

  val string = DynamoValue.s("dynosaur")
  val strings = DynamoValue.l((0 until 10).map { idx =>
    DynamoValue.s(s"test-$idx")
  }.toList)

  val schemaForStrings: Schema[Seq[String]] = Schema.seq(Schema.string)
}

class DecodingBench {

  import DecodingBench._

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def decodeAnS =
    Schema.string.read(string)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def decodeAnM =
    schermaForAllosaurus.read(allosaurusDv)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def decodeOneOfWithTag =
    schemaForDynosaurWithTag.read(tyrannosaurusRexWithTagDv)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def decodeOneOfWithDiscriminator =
    schemaForDynosaurWithDiscriminator.read(dynosaurDvWithDiscriminator)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def decodeList = schemaForStrings.read(strings)

}

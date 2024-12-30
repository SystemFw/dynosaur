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

import cats.data.NonEmptyList

object schemas {

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

  val schermaForAllosauruses =
    Schema.seq(schermaForAllosaurus)

  val schemaForStrings = Schema.seq(Schema.string)

}

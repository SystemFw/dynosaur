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

import com.ovoenergy.comms.aws.dynamodb.model.{AttributeName, AttributeValue}
import cats._, implicits._
import Schema.structure._

case class ParseError() extends Exception

trait Decoder[A] {
  def read(v: AttributeValue): Either[ParseError, A]
}
object Decoder {
  def instance[A](f: AttributeValue => Either[ParseError, A]): Decoder[A] =
    new Decoder[A] {
      def read(v: AttributeValue): Either[ParseError, A] = f(v)
    }

  def fromSchema[A](s: Schema[A]): Decoder[A] = {
    type Res[B] = Either[ParseError, B]

    def decodeInt: AttributeValue => Res[Int] =
      _.n.toRight(ParseError()).flatMap { v =>
        Either.catchNonFatal(v.value.toInt).leftMap(_ => ParseError())
      }

    def decodeString: AttributeValue => Res[String] =
      _.s.toRight(ParseError()).map(_.value)

    def decodeObject[R](
        record: Ap[Field[R, ?], R],
        v: AttributeValue.M): Res[R] =
      record.foldMap {
        λ[Field[R, ?] ~> Res] { field =>
          v.values
            .get(AttributeName(field.name))
            .toRight(ParseError())
            .flatMap { v =>
              fromSchema(field.elemSchema).read(v)
            }
        }
      }

    /**
      * Assumes a Map with a discriminator
      */
    def decodeSum[B](
        cases: List[Alt[B, C] forSome { type C }],
        v: AttributeValue.M): Res[B] =
      cases
        .foldMapK {
          case Alt(id, schema, review, _) =>
            v.values.get(AttributeName(id)).map { v => () =>
              fromSchema(schema).read(v).map(review)
            }
        }
        .toRight(ParseError())
        .flatMap(doDecode => doDecode())

    s match {
      case Num => Decoder.instance(decodeInt)
      case Str => Decoder.instance(decodeString)
      case Rec(rec) =>
        Decoder.instance {
          _.m.toRight(ParseError()).flatMap(decodeObject(rec, _))
        }
      case Sum(cases) =>
        Decoder.instance {
          _.m.toRight(ParseError()).flatMap(decodeSum(cases, _))
        }
    }
  }
}

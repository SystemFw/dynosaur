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

case class WriteError() extends Exception

trait Encoder[A] {
  def write(a: A): Either[WriteError, AttributeValue]
}
object Encoder {
  def instance[A](f: A => Either[WriteError, AttributeValue]): Encoder[A] =
    new Encoder[A] {
      def write(a: A) = f(a)
    }

  def fromSchema[A](s: Schema[A]): Encoder[A] = {
    type Res = Either[WriteError, AttributeValue]

    def encodeInt: Int => Res = AttributeValue.n(_).asRight
    def encodeString: String => Res = AttributeValue.s(_).asRight
    def encodeObject[R](record: Ap[Field[R, ?], R], v: R): Res =
      record
        .analyze {
          λ[Field[R, ?] ~> λ[a => Either[WriteError, AttributeValue.M]]] {
            field =>
              fromSchema(field.elemSchema).write(field.get(v)).map { av =>
                AttributeValue.M(Map(AttributeName(field.name) -> av))
              }
          }
        }
        .widen[AttributeValue]

    /**
      * Uses a Map with a discriminator
      */
    def encodeSum[B](cases: List[Alt[B]], v: B): Res =
      cases
        .foldMapK { alt =>
          alt.preview(v).map { e => () =>
            fromSchema(alt.caseSchema).write(e).map { av =>
              AttributeValue.M(Map(AttributeName(alt.id) -> av))
            }
          }
        }
        .map(doEncode => doEncode())
        .getOrElse(WriteError().asLeft)

    s match {
      case Num => Encoder.instance(encodeInt)
      case Str => Encoder.instance(encodeString)
      case Rec(rec) =>
        Encoder.instance(v => encodeObject(rec, v))
      case Sum(cases) => Encoder.instance(v => encodeSum(cases, v))
    }
  }
}

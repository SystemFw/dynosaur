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

trait Encoder[A] {
  def write(a: A): AttributeValue
}
object Encoder {
  def instance[A](f: A => AttributeValue): Encoder[A] = new Encoder[A] {
    def write(a: A): AttributeValue = f(a)
  }

  def fromSchema[A](s: Schema[A]): Encoder[A] = {
    def encodeInt: Int => AttributeValue = AttributeValue.n(_)
    def encodeString: String => AttributeValue = AttributeValue.s(_)
    def encodeObject[R](record: Ap[Field[R, ?], R], v: R): AttributeValue.M =
      record.analyze {
        λ[Field[R, ?] ~> λ[a => AttributeValue.M]] { field =>
          AttributeValue.M(
            Map(AttributeName(field.name) -> fromSchema(field.elemSchema)
              .write(field.get(v))))
        }
      }

    /**
      * Uses a Map with a discriminator
      */
    def encodeSum[B](
        cases: List[Alt[B, C] forSome { type C }],
        v: B): AttributeValue =
      cases
        .foldMapK {
          case Alt(id, schema, _, preview) =>
            preview(v).map { e => () =>
              AttributeValue.M(
                Map(AttributeName(id) -> fromSchema(schema).write(e)))
            }
        }
        .map(doEncode => doEncode())
        .get // TODO need to figure out what to do here, it's the conceptual equivalent of an incomplete match, we could/should just make encoding return either as well, would also handle malformed data in the case serialisation has constraints

    s match {
      case Num => Encoder.instance(encodeInt)
      case Str => Encoder.instance(encodeString)
      case Rec(rec) =>
        Encoder.instance(v => encodeObject(rec, v): AttributeValue)
      case Sum(cases) => Encoder.instance(v => encodeSum(cases, v))
    }
  }
}

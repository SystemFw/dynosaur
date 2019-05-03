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
import cats.~>
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

    s match {
      case Num => Encoder.instance(encodeInt)
      case Str => Encoder.instance(encodeString)
      case Rec(rec) =>
        Encoder.instance(v => encodeObject(rec, v): AttributeValue)
    }
  }
}

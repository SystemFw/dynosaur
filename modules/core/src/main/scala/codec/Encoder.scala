/*
 * Copyright 2019 OVO Energy
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
package codec

import cats._, implicits._
import cats.data.{Chain, WriterT}
import cats.free.Free

import model.{AttributeName, AttributeValue}
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

    def encodeObject[R](recordSchema: Free[Field[R, ?], R], record: R): Res = {
      def write[E](name: String, schema: Schema[E])(elem: E) =
        fromSchema(schema).write(elem).map { av =>
          AttributeValue.M(Map(AttributeName(name) -> av))
        }

      recordSchema
        .foldMap {
          λ[Field[R, ?] ~> WriterT[Either[WriteError, ?], AttributeValue.M, ?]] {
            case field: Field.Required[R, e] =>
              WriterT {
                val elem: e = field.get(record)
                write(field.name, field.elemSchema)(elem).tupleRight(elem)
              }
            case field: Field.Optional[R, e] =>
              WriterT {
                val elem: Option[e] = field.get(record)
                elem
                  .foldMap(write(field.name, field.elemSchema))
                  .tupleRight(elem)
              }
          }
        }
        .written
        .widen[AttributeValue]
    }

    def encodeSum[C](cases: Chain[Alt[C]], coproduct: C): Res =
      cases
        .foldMapK { alt =>
          alt.prism.tryGet(coproduct).map { elem =>
            fromSchema(alt.caseSchema).write(elem)
          }
        }
        .getOrElse(WriteError().asLeft)

    def encodeIsos[V](xmap: XMap[V], value: V): Res =
      xmap.w(value).flatMap(v => fromSchema(xmap.schema).write(v))

    s match {
      case Num => Encoder.instance(encodeInt)
      case Str => Encoder.instance(encodeString)
      case Record(rec) =>
        Encoder.instance(v => encodeObject(rec, v))
      case Sum(cases) => Encoder.instance(v => encodeSum(cases, v))
      case Isos(x) =>
        Encoder.instance(v => encodeIsos(x, v))
    }
  }
}

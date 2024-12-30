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

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode}
import cats.data.NonEmptyList
import scala.jdk.CollectionConverters._
import schemas._
import data._

class EncodingBench {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeAnS =
    Schema.string.write(string)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeRawAnM =
    DynamoValue.m(
      Map(
        "name" -> DynamoValue.s(allosaurus.name),
        "age" -> DynamoValue.n(allosaurus.age),
        "attacks" -> DynamoValue.n(allosaurus.attacks)
      )
    )

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeRawAnMAsAv =
    val map = new java.util.IdentityHashMap[String, software.amazon.awssdk.services.dynamodb.model.AttributeValue](3)
    map.put("name", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(allosaurus.name).build())
    map.put("age", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().n(allosaurus.age.toString).build())
    map.put("attacks", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().n(allosaurus.attacks.toString).build())
    DynamoValue(software.amazon.awssdk.services.dynamodb.model.AttributeValue
        .builder()
        .m(map)
        .build()
    )

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeAnM =
    schermaForAllosaurus.write(allosaurus)

}

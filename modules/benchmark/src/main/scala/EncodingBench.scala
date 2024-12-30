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

import cats.syntax.all.*

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode}
import cats.data.NonEmptyList

import schemas.*
import data.*

class EncodingBench {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeAnS =
    Schema.string.write(string)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  def encodeAnM =
    schermaForAllosaurus.write(allosaurus)

}

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

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Mode,
  OutputTimeUnit
}
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Scope

object DynamoValueBench {
  @State(Scope.Benchmark)
  val aDynamoValueM = DynamoValue.m(
    (0 until 100).map(idx => s"test-$idx" -> DynamoValue.s(idx.toString)): _*
  )
}

class DynamoValueBench {

  // @Benchmark
  // @BenchmarkMode(Array(Mode.AverageTime))
  // @OutputTimeUnit(TimeUnit.MICROSECONDS)
  // def createDynamoValueS = DynamoValue.s("Foo")

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def dynamoValueM = DynamoValueBench.aDynamoValueM.m

}

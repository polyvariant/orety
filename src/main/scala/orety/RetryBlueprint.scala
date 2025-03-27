/*
 * Copyright 2025 Polyvariant
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

package orety

import cats.syntax.all.*
import cats.{Applicative, MonadError}
import cats.effect.IO
import retry.*
import scala.concurrent.duration._
import scala.collection.LinearSeq

object RetryBlueprint {
  def fibonacciBackoff(baseDelay: FiniteDuration): LazyList[FiniteDuration] = {
    fib(baseDelay.length)
      .map(_.min(BigInt(Long.MaxValue)).longValue)
      .map(l => FiniteDuration(l, baseDelay.unit))
  }

  def fib(base: BigInt): LazyList[BigInt] = {
    base #:: base #:: fib(base).zip(fib(base).tail).map { n =>
      n._1 + n._2
    }
  }

  def capDelay(cap: FiniteDuration, blueprint: LazyList[FiniteDuration]): LazyList[FiniteDuration] =
    blueprint.map(_.min(cap))

  def limitRetriesByCumulativeDelay(
      threshold: FiniteDuration,
      blueprint: LazyList[FiniteDuration]
  ): LazyList[FiniteDuration] = blueprint.scanLeft(0.nanos) { (cumulativeDelay, current) =>
    cumulativeDelay + current
  }.takeWhile(_ <= threshold).tail.zip(blueprint).map(_._2)
}

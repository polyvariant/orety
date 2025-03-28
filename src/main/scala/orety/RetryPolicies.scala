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

import java.util.concurrent.TimeUnit

import cats.Applicative
import cats.effect.std.Random
import cats.syntax.functor.*
import cats.syntax.show.*
import orety.PolicyDecision.*

import scala.concurrent.duration.{Duration, FiniteDuration}

object RetryPolicies:
  private val LongMax: BigInt = BigInt(Long.MaxValue)

  /*
   * Multiply the given duration by the given multiplier, but cap the result to
   * ensure we don't try to create a FiniteDuration longer than 2^63 - 1 nanoseconds.
   *
   * Note: despite the "safe" in the name, we can still create an invalid
   * FiniteDuration if the multiplier is negative. But an assumption of the library
   * as a whole is that nobody would be silly enough to use negative delays.
   */
  private def safeMultiply(
      duration: FiniteDuration,
      multiplier: Long
  ): FiniteDuration =
    val durationNanos   = BigInt(duration.toNanos)
    val resultNanos     = durationNanos * BigInt(multiplier)
    val safeResultNanos = resultNanos min LongMax
    FiniteDuration(safeResultNanos.toLong, TimeUnit.NANOSECONDS)

  /** Don't retry at all and always give up. Only really useful for combining with other policies.
    */
  def alwaysGiveUp[F[_]: Applicative]: RetryPolicy[F, Any] = RetryPolicy.liftWithShow((_, _) => GiveUp, "alwaysGiveUp")

  /** Delay by a constant amount before each retry. Never give up.
    */
  def constantDelay[F[_]: Applicative](delay: FiniteDuration): RetryPolicy[F, Any] = RetryPolicy.liftWithShow(
    (_, _) => DelayAndRetry(delay),
    show"constantDelay($delay)"
  )

  /** Each delay is twice as long as the previous one. Never give up.
    */
  def exponentialBackoff[F[_]: Applicative](
      baseDelay: FiniteDuration
  ): RetryPolicy[F, Any] = RetryPolicy.liftWithShow(
    { (_, status) =>
      val delay = safeMultiply(
        baseDelay,
        Math.pow(2.0, status.retriesSoFar.toDouble).toLong
      )
      DelayAndRetry(delay)
    },
    show"exponentialBackOff(baseDelay=$baseDelay)"
  )

  /** Retry without delay, giving up after the given number of retries.
    */
  def limitRetries[F[_]: Applicative](maxRetries: Int): RetryPolicy[F, Any] = RetryPolicy.liftWithShow(
    { (_, status) =>
      if status.retriesSoFar >= maxRetries then GiveUp
      else DelayAndRetry(Duration.Zero)
    },
    show"limitRetries(maxRetries=$maxRetries)"
  )

  /** "Full jitter" backoff algorithm. See
    * https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
    */
  def fullJitter[F[_]: Applicative: Random](baseDelay: FiniteDuration): RetryPolicy[F, Any] = RetryPolicy.withShow(
    { (_, status) =>
      val e        = Math.pow(2.0, status.retriesSoFar.toDouble).toLong
      val maxDelay = safeMultiply(baseDelay, e)
      Random[F].nextDouble.map { rnd =>
        val delayNanos = (maxDelay.toNanos * rnd).toLong
        DelayAndRetry(new FiniteDuration(delayNanos, TimeUnit.NANOSECONDS))
      }
    },
    show"fullJitter(baseDelay=$baseDelay)"
  )

  /** Set an upper bound on any individual delay produced by the given policy.
    */
  def capDelay[F[_]: Applicative, Res](
      cap: FiniteDuration,
      policy: RetryPolicy[F, Res]
  ): RetryPolicy[F, Res] = policy.meet(constantDelay(cap))

  /** Add an upper bound to a policy such that once the given time-delay amount <b>per try</b> has been
    * reached or exceeded, the policy will stop retrying and give up. If you need to stop retrying once
    * <b>cumulative</b> delay reaches a time-delay amount, use [[limitRetriesByCumulativeDelay]].
    */
  def limitRetriesByDelay[F[_]: Applicative, Res](
      threshold: FiniteDuration,
      policy: RetryPolicy[F, Res]
  ): RetryPolicy[F, Res] =
    def decideNextRetry(actionResult: Res, status: RetryStatus): F[PolicyDecision] =
      policy.decideNextRetry(actionResult, status).map {
        case r @ DelayAndRetry(delay) => if delay > threshold then GiveUp else r
        case GiveUp                   => GiveUp
      }

    RetryPolicy.withShow[F, Res](
      decideNextRetry,
      show"limitRetriesByDelay(threshold=$threshold, $policy)"
    )

  /** Add an upperbound to a policy such that once the cumulative delay over all retries has reached or
    * exceeded the given limit, the policy will stop retrying and give up.
    */
  def limitRetriesByCumulativeDelay[F[_]: Applicative, Res](
      threshold: FiniteDuration,
      policy: RetryPolicy[F, Res]
  ): RetryPolicy[F, Res] =
    def decideNextRetry(actionResult: Res, status: RetryStatus): F[PolicyDecision] =
      policy.decideNextRetry(actionResult, status).map {
        case r @ DelayAndRetry(delay) => if status.cumulativeDelay + delay >= threshold then GiveUp else r
        case GiveUp                   => GiveUp
      }

    RetryPolicy.withShow[F, Res](
      decideNextRetry,
      show"limitRetriesByCumulativeDelay(threshold=$threshold, $policy)"
    )

  /** Build a dynamic retry policy that chooses the retry policy based on the result of the last attempt
    */
  def dynamic[F[_], Res](f: Res => RetryPolicy[F, Res]): RetryPolicy[F, Res] =
    def decideNextRetry(actionResult: Res, status: RetryStatus): F[PolicyDecision] =
      val policy = f(actionResult)
      policy.decideNextRetry(actionResult, status)

    RetryPolicy.withShow[F, Res](
      decideNextRetry,
      show"dynamic(<function>)"
    )
end RetryPolicies

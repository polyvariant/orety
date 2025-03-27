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

import cats.{Applicative, Functor}
import cats.syntax.functor.*

/** A handler that inspects the result of an action and decides what to do next. This is also a good place to
  * do any logging.
  */
type ResultHandler[F[_], -Res, A] = (Res, RetryDetails) => F[HandlerDecision[F[A]]]

// Type aliases for different flavours of handler
type ValueHandler[F[_], A]        = ResultHandler[F, A, A]
type ErrorHandler[F[_], A]        = ResultHandler[F, Throwable, A]
type ErrorOrValueHandler[F[_], A] = ResultHandler[F, Either[Throwable, A], A]

object ResultHandler:
  /** Construct an ErrorHandler that always chooses to retry the same action, no matter what the error.
    *
    * @param log
    *   A chance to do logging, increment metrics, etc
    */
  def retryOnAllErrors[F[_]: Functor, A](
      log: (Throwable, RetryDetails) => F[Unit]
  ): ErrorHandler[F, A] =
    (error: Throwable, retryDetails: RetryDetails) => log(error, retryDetails).as(HandlerDecision.Continue)

  /** Construct an ErrorHandler that chooses to retry the same action as long as the error is worth retrying.
    *
    * @param log
    *   A chance to do logging, increment metrics, etc
    */
  def retryOnSomeErrors[F[_]: Functor, A](
      isWorthRetrying: Throwable => Boolean,
      log: (Throwable, RetryDetails) => F[Unit]
  ): ErrorHandler[F, A] = (error: Throwable, retryDetails: RetryDetails) =>
    log(error, retryDetails)
      .as(if isWorthRetrying(error) then HandlerDecision.Continue else HandlerDecision.Stop)

  /** Construct a ValueHandler that chooses to retry the same action until it returns a successful result.
    *
    * @param log
    *   A chance to do logging, increment metrics, etc
    */
  def retryUntilSuccessful[F[_]: Functor, A](
      isSuccessful: A => Boolean,
      log: (A, RetryDetails) => F[Unit]
  ): ValueHandler[F, A] = (value: A, retryDetails: RetryDetails) =>
    log(value, retryDetails)
      .as(if isSuccessful(value) then HandlerDecision.Stop else HandlerDecision.Continue)

  /** Pass this to [[retryOnAllErrors]] or [[retryUntilSuccessful]] if you don't need to do any logging */
  def noop[F[_]: Applicative, A]: (A, RetryDetails) => F[Unit] = (_, _) => Applicative[F].unit
end ResultHandler

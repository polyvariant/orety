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

import cats.Functor
import cats.effect.Temporal
import cats.syntax.apply.*
import cats.syntax.functor.*
import cats.syntax.flatMap.*

import scala.concurrent.duration.FiniteDuration
import cats.Applicative

/*
 * API enhacement
 */

def retryingOnErrors[F[_], A](
    action: F[A]
)(
    blueprint: LazyList[FiniteDuration],
    errorHandler: ErrorHandler[F, A]
)(using
    T: Temporal[F]
): F[A] = T.tailRecM((action, RetryStatus.NoRetriesYet, blueprint)) { (currentAction, status, blueprint) =>
  T.attempt(currentAction).flatMap { attempt =>
    retryingOnErrorsImpl(blueprint, ResultHandler.retryOnAllErrors(ResultHandler.noop), status, currentAction, attempt)
  }
}

/*
 * Implementation
 */

private def retryingOnErrorsImpl[F[_], A](
    blueprint: LazyList[FiniteDuration],
    errorHandler: ErrorHandler[F, A],
    status: RetryStatus,
    currentAction: F[A],
    attempt: Either[Throwable, A]
)(using
    T: Temporal[F]
): F[Either[(F[A], RetryStatus, LazyList[FiniteDuration]), A]] =

  def applyNextStep(
      error: Throwable,
      nextStep: NextStep,
      nextAction: F[A]
  ): F[Either[(F[A], RetryStatus, LazyList[FiniteDuration]), A]] = nextStep match
    case NextStep.RetryAfterDelay(delay, updatedStatus) => T.sleep(delay) *>
      T.pure(Left(nextAction, updatedStatus, blueprint.tail)) // continue recursion
    case NextStep.GiveUp => T.raiseError[A](error).map(Right(_)) // stop the recursion

  def applyHandlerDecision(
      error: Throwable,
      handlerDecision: HandlerDecision[F[A]],
      nextStep: NextStep
  ): F[Either[(F[A], RetryStatus, LazyList[FiniteDuration]), A]] = handlerDecision match
    case HandlerDecision.Stop             =>
      // Error is not worth retrying. Stop the recursion and raise the error.
      T.raiseError[A](error).map(Right(_))
    case HandlerDecision.Continue         =>
      // Depending on what the retry policy decided,
      // either delay and then retry the same action, or give up
      applyNextStep(error, nextStep, currentAction)
    case HandlerDecision.Adapt(newAction) =>
      // Depending on what the retry policy decided,
      // either delay and then try a new action, or give up
      applyNextStep(error, nextStep, newAction)

  attempt match
    case Left(error)    =>
      val nextStep     =
        blueprint.headOption.map(delay => NextStep.RetryAfterDelay(delay, status.addRetry(delay))).getOrElse(
          NextStep.GiveUp
        )
      val retryDetails = buildRetryDetails(status, nextStep)
      for
        handlerDecision <- errorHandler(error, retryDetails)
        result          <- applyHandlerDecision(error, handlerDecision, nextStep)
      yield result
    case Right(success) => T.pure(Right(success)) // stop the recursion
end retryingOnErrorsImpl

private[orety] def buildRetryDetails(
    currentStatus: RetryStatus,
    nextStep: NextStep
): RetryDetails = nextStep match
  case NextStep.RetryAfterDelay(delay, _) => RetryDetails(
      currentStatus.retriesSoFar,
      currentStatus.cumulativeDelay,
      RetryDetails.NextStep.DelayAndRetry(delay)
    )
  case NextStep.GiveUp                    => RetryDetails(
      currentStatus.retriesSoFar,
      currentStatus.cumulativeDelay,
      RetryDetails.NextStep.GiveUp
    )

private[orety] sealed trait NextStep

private[orety] object NextStep:
  case object GiveUp extends NextStep

  final case class RetryAfterDelay(
      delay: FiniteDuration,
      updatedStatus: RetryStatus
  ) extends NextStep

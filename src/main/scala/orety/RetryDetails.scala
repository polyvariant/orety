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

import scala.concurrent.duration.FiniteDuration

/** A collection of information about retries that could be useful for logging
  *
  * @param retriesSoFar
  *   How many retries have occurred so far. Will be zero or more. The total number of attempts so far is one
  *   more than this number.
  * @param cumulativeDelay
  *   The total time we have spent delaying between retries so far.
  * @param nextStepIfUnsuccessful
  *   What the retry policy has chosen to do next if the latest attempt is not successful.
  */
case class RetryDetails(
    retriesSoFar: Int,
    cumulativeDelay: FiniteDuration,
    nextStepIfUnsuccessful: RetryDetails.NextStep
)

object RetryDetails:

  enum NextStep:
    case GiveUp
    case DelayAndRetry(nextDelay: FiniteDuration)

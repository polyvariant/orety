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

/** The result of inspecting a result or error and deciding what to do next
  */
enum HandlerDecision[+FA]:
  /** We are finished, either because the action returned a successful value, or because it raised an error so
    * heinous we don't want to retry.
    */
  case Stop

  /** Try the same action again, as long as the retry policy says it's OK to continue.
    */
  case Continue

  /** Switch to a new action for subsequent retries, as long as the retry policy says it's OK to continue.
    */
  case Adapt(newAction: FA)

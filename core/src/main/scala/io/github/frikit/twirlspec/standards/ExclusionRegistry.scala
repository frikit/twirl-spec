/*
 * Copyright 2026 frikiT
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

package io.github.frikit.twirlspec.standards

import scala.collection.concurrent.TrieMap

/** Remembers, per spec, whether each rule it excluded with
  * `meetStandardsExcept` would have found anything on the pages it was excluded
  * from. An exclusion whose rule never fires is doing nothing, and has usually
  * outlived the fix that made it unnecessary.
  */
private[twirlspec] object ExclusionRegistry {

  private val firedBySpec = TrieMap.empty[String, TrieMap[String, Boolean]]

  /** Remember that this spec excluded this rule from a page, and whether the
    * rule found anything there. Once it has fired, it stays fired.
    */
  def record(spec: String, ruleId: String, fired: Boolean): Unit = {
    val rules = firedBySpec.getOrElseUpdate(spec, TrieMap.empty)
    if (fired) rules.put(ruleId, true) else rules.putIfAbsent(ruleId, false)
  }

  /** The rules this spec excluded that never found anything, sorted. */
  def unused(spec: String): Seq[String] =
    firedBySpec
      .get(spec)
      .map(_.collect { case (id, false) => id }.toSeq.sorted)
      .getOrElse(Nil)

  /** Forget a spec's exclusions. */
  def forget(spec: String): Unit = firedBySpec.remove(spec)
}

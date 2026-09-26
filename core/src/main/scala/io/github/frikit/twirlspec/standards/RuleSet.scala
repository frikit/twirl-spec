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

import io.github.frikit.twirlspec.expect.Expectation

/** A named collection of rules. */
trait RuleSet {

  /** Every rule in this set, at its natural severity. */
  def all: Seq[Rule]

  /** Every rule but the ones with these ids, each of which must be in the set.
    */
  def allExcept(ids: String*): Seq[Rule] = {
    requireKnown(ids)
    all.filterNot(r => ids.contains(r.id))
  }

  /** Only the rules with these ids, each of which must be in the set. */
  def only(ids: String*): Seq[Rule] = {
    requireKnown(ids)
    all.filter(r => ids.contains(r.id))
  }

  /** A misspelt id would select or exclude nothing without saying so, so it is
    * refused, with the ids the set does have.
    */
  private def requireKnown(ids: Seq[String]): Unit = {
    val unknown = ids.filterNot(id => all.exists(_.id == id)).distinct
    if (unknown.nonEmpty)
      throw new IllegalArgumentException(
        s"${getClass.getSimpleName.stripSuffix("$")} has no rule " +
          s"${unknown.mkString(", ")}; its rules are ${all.map(_.id).mkString(", ")}"
      )
  }

  /** This set, or any subset of it, as one expectation. */
  def expectation(rules: Seq[Rule] = all): Expectation = Rule.expectation(rules)
}

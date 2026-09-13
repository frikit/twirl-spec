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

  def allExcept(ids: String*): Seq[Rule] = all.filterNot(r => ids.toSet.contains(r.id))

  def only(ids: String*): Seq[Rule] = all.filter(r => ids.toSet.contains(r.id))

  /** This set, or any subset of it, as one expectation. */
  def expectation(rules: Seq[Rule] = all): Expectation = Rule.expectation(rules)
}

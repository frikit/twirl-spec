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

import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  /** Every module is at 100% statement and branch, and the gate is set there so
    * it stays that way: a new branch has to be covered, excluded with a
    * `$COVERAGE-OFF$` marker and a reason, or deliberately dropped by lowering
    * this number in a commit someone can see.
    */
  def apply(statement: Int = 100, branch: Int = 100): Seq[Setting[?]] = Seq(
    coverageExcludedPackages := "<empty>;Reverse.*;..*Routes.*;",
    coverageMinimumStmtTotal := statement,
    coverageMinimumBranchTotal := branch,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

}

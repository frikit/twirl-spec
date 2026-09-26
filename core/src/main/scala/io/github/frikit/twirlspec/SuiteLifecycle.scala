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

package io.github.frikit.twirlspec

import io.github.frikit.twirlspec.page.CoverageRegistry
import io.github.frikit.twirlspec.standards.ExclusionRegistry
import org.scalatest.{Args, Status, Suite, SuiteMixin}

/** Starts each run of a suite with a clean coverage and exclusion record, and
  * drops both once it has finished, so a spec run again in the same JVM — from
  * an sbt shell, say — cannot pass `assertEverything` or `unusedExclusions` on
  * what an earlier run asserted.
  *
  * [[TwirlSpec]] mixes it in. A spec base built on [[TwirlSpecDsl]] mixes it in
  * itself:
  *
  * {{{
  * trait ViewSpecBase extends AnyWordSpec with Matchers with GuiceOneAppPerSuite
  *   with TwirlSpecDsl with SuiteLifecycle
  * }}}
  *
  * The run `OneInstancePerTest` (and so `ParallelTestExecution`) makes for each
  * test on its own instance leaves the records alone, since the suite's other
  * tests are still adding to them; ScalaTest marks that run with
  * `runTestInNewInstance`. A `BeforeAndAfterAll` mixed in after this trait runs
  * its `afterAll` once the records have been dropped.
  */
trait SuiteLifecycle extends SuiteMixin { this: Suite =>

  abstract override def run(testName: Option[String], args: Args): Status =
    if (args.runTestInNewInstance) super.run(testName, args)
    else {
      val spec = getClass.getName
      val forget = () => {
        CoverageRegistry.forget(spec)
        ExclusionRegistry.forget(spec)
      }
      forget()
      val status = super.run(testName, args)
      status.whenCompleted(_ => forget())
      status
    }

}

/*
 * Copyright 2026 Victor Osipov
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

package io.github.frikit.twirlspec.render

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

/** One Play application per distinct configuration, shared by every spec in the JVM. */
object SharedApplication {

  /** Configuration every view test wants: no metrics, no auditing, no CSP nonce. Languages are the service's to declare. */
  val viewTestDefaults: Map[String, Any] = Map(
    "metrics.enabled"                -> false,
    "auditing.enabled"               -> false,
    "play.i18n.langs"                -> Seq("en"),
    "play.filters.csp.nonce.enabled" -> false
  )

  private val cache = new ApplicationCache(config => new GuiceApplicationBuilder().configure(config).build())

  /** The application for this configuration, building it on first use. */
  def apply(configuration: Map[String, Any]): Application = cache(viewTestDefaults ++ configuration)

  /** How many applications this JVM has built — asserted by twirl-spec's own tests, and useful in a service that suspects it is still booting per spec.
    */
  def instanceCount: Int = cache.instanceCount

  /** Stops every cached application and forgets it. */
  private[twirlspec] val shutdownHook: Runnable = new Runnable {
    def run(): Unit = cache.reset()
  }

  Runtime.getRuntime.addShutdownHook(new Thread(shutdownHook))

}

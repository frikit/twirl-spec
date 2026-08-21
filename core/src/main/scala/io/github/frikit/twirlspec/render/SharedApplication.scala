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

/** One Play application per distinct configuration, shared by every spec in the
  * JVM.
  *
  * A common helper builds and stops a fresh Guice application for every single
  * view it instantiates: `applicationBuilder(...).build()`, pull the view,
  * `application.stop()`. Across a few hundred view specs that is a great many
  * Play boots per CI run, for something that renders HTML and holds no state.
  *
  * Views are stateless and the application is only ever read from, so one
  * instance per configuration is safe and is the whole difference between a
  * view suite that takes minutes and one that takes seconds.
  */
object SharedApplication {

  /** Configuration every view test wants: no metrics, no auditing, both langs. */
  val viewTestDefaults: Map[String, Any] = Map(
    "metrics.enabled"                -> false,
    "auditing.enabled"               -> false,
    "play.i18n.langs"                -> Seq("en", "cy"),
    "play.filters.csp.nonce.enabled" -> false
  )

  private val cache = new ApplicationCache(config => new GuiceApplicationBuilder().configure(config).build())

  /** The application for this configuration, building it on first use. */
  def apply(configuration: Map[String, Any]): Application = cache(viewTestDefaults ++ configuration)

  /** How many applications this JVM has built — asserted by twirl-spec's own
    * tests, and useful in a service that suspects it is still booting per spec.
    */
  def instanceCount: Int = cache.instanceCount

  /** Stops every cached application and forgets it.
    *
    * Registered as a JVM shutdown hook, and exposed so it can be exercised
    * directly. It resets rather than only stopping, so that running it leaves
    * the cache able to rebuild instead of handing out a stopped application.
    */
  private[twirlspec] val shutdownHook: Runnable = new Runnable {
    def run(): Unit = cache.reset()
  }

  Runtime.getRuntime.addShutdownHook(new Thread(shutdownHook))

}

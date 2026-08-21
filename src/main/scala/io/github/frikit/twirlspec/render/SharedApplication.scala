/*
 * Copyright 2026 HM Revenue & Customs
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

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

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

  private val applications = new ConcurrentHashMap[Map[String, Any], Application]()

  /** The application for this configuration, building it on first use. */
  def apply(configuration: Map[String, Any]): Application = {
    val effective = viewTestDefaults ++ configuration
    applications.computeIfAbsent(
      effective,
      new java.util.function.Function[Map[String, Any], Application] {
        def apply(config: Map[String, Any]): Application =
          new GuiceApplicationBuilder().configure(config).build()
      }
    )
  }

  /** How many applications this JVM has built — asserted by twirl-spec's own
    * tests, and useful in a service that suspects it is still booting per spec.
    */
  def instanceCount: Int = applications.size()

  // $COVERAGE-OFF$
  // The hook body runs at JVM exit, and reset() stops the application the rest
  // of the suite is sharing. Exercising either from a test would make the suite
  // order-dependent in order to prove nothing, so they are excluded here rather
  // than hidden behind a lower threshold.

  private val shutdown = new Thread(new Runnable {
    def run(): Unit = applications.values().asScala.foreach { app =>
      try play.api.Play.stop(app)
      catch { case _: Throwable => () }
    }
  })

  Runtime.getRuntime.addShutdownHook(shutdown)

  /** Drop the cache. Only needed by tests of the cache itself. */
  def reset(): Unit = {
    applications
      .values()
      .asScala
      .foreach(app =>
        try play.api.Play.stop(app)
        catch { case _: Throwable => () }
      )
    applications.clear()
  }
  // $COVERAGE-ON$

}

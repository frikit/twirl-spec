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

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/** A cache of Play applications keyed by configuration. */
final private[twirlspec] class ApplicationCache(build: Map[String, Any] => Application) {

  private val applications = new ConcurrentHashMap[Map[String, Any], Application]()

  /** The application for this configuration, building it on first use. */
  def apply(configuration: Map[String, Any]): Application =
    applications.computeIfAbsent(
      configuration,
      new java.util.function.Function[Map[String, Any], Application] {
        def apply(config: Map[String, Any]): Application = build(config)
      }
    )

  def instanceCount: Int = applications.size()

  /** Stop every cached application. No try/catch: Play already absorbs a double stop and a failing stop hook. */
  def stopAll(): Unit = applications.values().asScala.foreach(play.api.Play.stop)

  /** Stop everything and forget it. */
  def reset(): Unit = {
    stopAll()
    applications.clear()
  }

}

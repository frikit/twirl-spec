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

import sbt.*

object LibDependencies {

  // Compiled against the oldest supported Play on purpose: `Provided` means the consumer supplies its own, and
  // building against the oldest 3.0.x keeps the artifact usable by every consumer on that line.
  private val playVersion = "3.0.0"
  private val jsoupVersion = "1.23.2"
  private val scalatestVersion = "3.2.20"

  /** Provided dependencies are not transitive, so every module that touches
    * Play or ScalaTest types has to declare them, not just the core.
    */
  val shared: Seq[ModuleID] = Seq(
    "org.playframework" %% "play" % playVersion % Provided,
    "org.playframework" %% "play-test" % playVersion % Provided,
    "org.playframework" %% "play-guice" % playVersion % Provided,
    "org.scalatest" %% "scalatest" % scalatestVersion
  )

  val core: Seq[ModuleID] = shared :+ ("org.jsoup" % "jsoup" % jsoupVersion)

  val rules: Seq[ModuleID] = shared
  val messages: Seq[ModuleID] = shared

}

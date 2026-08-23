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
package io.github.frikit.twirlspec.page

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/** Remembers which elements a spec's assertions actually looked at.
  *
  * A spec renders the same page many times over — once per test — so the record
  * is keyed by the page's own html rather than by a  instance. Two renders
  * that produce identical markup are the same page, and their assertions add up.
  */
object CoverageRegistry {

  private val touchedBySource = TrieMap.empty[String, mutable.Set[String]]

  def record(sourceKey: String, paths: Iterable[String]): Unit =
    if (paths.nonEmpty) {
      val set = touchedBySource.getOrElseUpdate(sourceKey, mutable.Set.empty[String])
      set.synchronized(set ++= paths)
    }

  def touched(sourceKey: String): Set[String] =
    touchedBySource.get(sourceKey).map(s => s.synchronized(s.toSet)).getOrElse(Set.empty)

  def reset(): Unit = touchedBySource.clear()
}

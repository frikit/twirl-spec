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

package io.github.frikit.twirlspec.page

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/** Remembers which elements a spec's assertions actually looked at.
  *
  * A spec renders the same page many times over — once per test — so the record
  * is kept per coverage group rather than per page instance. A page rendered
  * through the DSL belongs to its spec and, within it, to the renders that
  * produce exactly its markup, unless the spec names a group with `coveredAs`.
  * The key is `spec#group`, so a spec's records can be dropped together. A page
  * built directly belongs to no spec and keeps its record on itself, so it
  * never appears here.
  */
object CoverageRegistry {

  private val touchedBySource = TrieMap.empty[String, mutable.Set[String]]

  /** Remember that an assertion in this coverage group touched these anchors.
    */
  def record(sourceKey: String, paths: Iterable[String]): Unit =
    if (paths.nonEmpty) {
      val set =
        touchedBySource.getOrElseUpdate(sourceKey, mutable.Set.empty[String])
      set.synchronized(set ++= paths)
    }

  /** Every anchor an assertion in this coverage group has touched so far. */
  def touched(sourceKey: String): Set[String] =
    touchedBySource
      .get(sourceKey)
      .map(s => s.synchronized(s.toSet))
      .getOrElse(Set.empty)

  /** Forget every record a spec made, so a spec run again in the same JVM
    * starts clean and a finished one leaves nothing behind.
    */
  def forget(spec: String): Unit =
    touchedBySource.keys
      .filter(_.startsWith(s"$spec#"))
      .foreach(touchedBySource.remove)

  /** Forget everything, for a spec that measures itself from a clean slate. */
  def reset(): Unit = touchedBySource.clear()
}

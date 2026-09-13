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

package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.page.{Anchors, CoverageRegistry, Page}
import org.jsoup.nodes.Element

import scala.jdk.CollectionConverters._

/** What a page puts on screen that a spec never asked about.
  *
  * Rules answer "is this page sound". This answers the other question: "did the
  * spec actually look at it". A view can pass every standard while half of it
  * goes unasserted, because rendering a page is not the same as testing it.
  */
object ContentCoverage {

  /** Something a reader can see or act on, so something a spec ought to assert.
    */
  final case class Anchor(kind: String, name: String, path: String)

  /** Attributes that mark an element as worth asserting, beyond ids and links.
    */
  val defaultTrackedAttributes: Set[String] = Anchors.defaultTrackedAttributes

  /** A view spec answers for the page's own content, not for the header, footer
    * and cookie banner every page inherits from the layout.
    */
  val defaultScope: String = "main, #main-content"

  /** Everything on the page, within the scope, that a spec ought to assert. */
  def anchors(
      page: Page,
      trackedAttributes: Set[String] = defaultTrackedAttributes,
      scope: String = defaultScope
  ): List[Anchor] =
    within(page, scope)
      .flatMap(e =>
        Anchors.namesOf(e, trackedAttributes).map(n => Anchor(kindOf(n), n, n))
      )
      .distinctBy(_.name)

  /** Everything inside the scope, but not the container itself — a spec does
    * not assert the wrapper it was handed.
    */
  private def within(page: Page, scope: String): List[Element] = {
    val scoped = page.document.select(scope)
    if (scoped.isEmpty) page.document.getAllElements.asScala.toList
    else {
      val root = scoped.first()
      root.getAllElements.asScala.toList.filterNot(_ eq root)
    }
  }

  private def kindOf(name: String): String =
    if (name.startsWith("#")) "id"
    else if (name.startsWith("link ")) "link"
    else "tracking"

  /** The anchors on this page that no assertion has touched. */
  def unasserted(
      page: Page,
      ignored: Set[String] = Set.empty,
      trackedAttributes: Set[String] = defaultTrackedAttributes,
      scope: String = defaultScope
  ): List[Anchor] = {
    val touched = CoverageRegistry.touched(page.coverageGroup)
    val ignoredIds = ignored.map(_.stripPrefix("#"))
    within(page, scope)
      .filterNot(insideIgnored(_, ignoredIds))
      .flatMap(e =>
        Anchors.namesOf(e, trackedAttributes).map(n => Anchor(kindOf(n), n, n))
      )
      .distinctBy(_.name)
      .filterNot(a => touched.contains(a.name))
      .filterNot(a =>
        ignored.contains(a.name) || ignored.contains(a.name.stripPrefix("#"))
      )
  }

  /** Ignoring a block means ignoring what it holds: a spec that disclaims the
    * layout's "report a technical issue" wrapper is not asking to be held to
    * the link inside it.
    */
  private def insideIgnored(e: Element, ignoredIds: Set[String]): Boolean =
    ignoredIds.nonEmpty && (Iterator(e) ++ e.parents.asScala.iterator).exists(
      a => ignoredIds.contains(a.id())
    )

  /** The unasserted anchors as a readable list, grouped by kind. */
  def report(unasserted: List[Anchor]): String = {
    val byKind = unasserted.groupBy(_.kind)
    val lines = List("id", "link", "tracking").flatMap { kind =>
      byKind.get(kind).map { as =>
        s"  $kind (${as.size}): ${as.map(_.name).mkString(", ")}"
      }
    }
    (s"${unasserted.size} things on this page were never asserted:" :: lines)
      .mkString("\n")
  }

}

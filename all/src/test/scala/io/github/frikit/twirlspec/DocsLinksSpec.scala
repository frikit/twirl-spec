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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** The documentation is read in two places, and a link has to work in both: in
  * the repository on GitHub, and on the site built from it at
  * frikit.github.io/twirl-spec.
  *
  * The site publishes the README, the changelog and the `docs` folder as pages,
  * and rewrites a relative link to a `.md` file into a link to the built page.
  * It publishes nothing else: `CONTRIBUTING.md` is one of the repository
  * metadata files Jekyll's optional-front-matter plugin leaves alone, and
  * `LICENSE` and `build.sbt` are not pages at all. A published page must
  * therefore reach those through their address on GitHub, or the link is a 404
  * for every reader of the site.
  */
class DocsLinksSpec extends AnyWordSpec with Matchers {

  private val root: File = {
    val start = new File(sys.props("user.dir")).getAbsoluteFile
    Iterator
      .iterate(start)(_.getParentFile)
      .takeWhile(_ != null)
      .find(dir => new File(dir, "README.md").isFile)
      .getOrElse(fail(s"no README.md at or above $start"))
  }

  private def read(path: String): String =
    new String(
      Files.readAllBytes(new File(root, path).toPath),
      StandardCharsets.UTF_8
    )

  private def guides: List[String] =
    Option(new File(root, "docs").listFiles())
      .getOrElse(Array.empty[File])
      .map(_.getName)
      .filter(_.endsWith(".md"))
      .sorted
      .map("docs/" + _)
      .toList

  /** The pages the site builds. */
  private val published: List[String] =
    "README.md" :: "CHANGELOG.md" :: guides

  /** Everything checked for broken links, published or not. */
  private val documentation: List[String] = "CONTRIBUTING.md" :: published

  private val MarkdownLink = """\[[^\]]*\]\(([^)\s]+)\)""".r

  /** Every link in a file, as (the file, the target it names, where the target
    * resolves to from the repository root), leaving out absolute ones and
    * anchors within a page.
    */
  private def linksIn(path: String): List[(String, String, String)] = {
    val from = new File(root, path).toPath.getParent
    MarkdownLink
      .findAllMatchIn(read(path))
      .map(_.group(1))
      .filterNot(target =>
        target.startsWith("http://") || target.startsWith("https://") ||
          target.startsWith("mailto:") || target.startsWith("#")
      )
      .map { target =>
        val file = target.takeWhile(_ != '#')
        val resolved = from.resolve(file).normalize()
        (path, target, root.toPath.relativize(resolved).toString)
      }
      .toList
  }

  private def allLinks: List[(String, String, String)] =
    documentation.flatMap(linksIn)

  "every relative link in the documentation" should {

    "name a file that exists" in {
      val missing = allLinks.filterNot { case (_, _, resolved) =>
        new File(root, resolved).exists()
      }
      withClue(
        missing
          .map { case (file, target, _) => s"$file links to $target" }
          .mkString("\n", "\n", "\n")
      )(missing mustBe empty)
    }

    "name a page the site publishes, when the page it is written on is published itself" in {
      val offSite = allLinks.filter { case (file, _, resolved) =>
        published.contains(file) && !published.contains(resolved)
      }
      withClue(
        offSite
          .map { case (file, target, _) =>
            s"$file links to $target, which the site does not publish; use its https://github.com/frikit/twirl-spec/blob/main/ address"
          }
          .mkString("\n", "\n", "\n")
      )(offSite mustBe empty)
    }

  }

  "every link in the documentation" should {

    "sit on one line" in {
      val split = documentation.flatMap { path =>
        MarkdownLink
          .findAllMatchIn(read(path))
          .map(_.group(0))
          .filter(_.contains("\n"))
          .map(link => s"$path: ${link.replace("\n", " ")}")
      }
      withClue(
        "the site rewrites a link to a page only when it is written on one line" +
          split.mkString("\n", "\n", "\n")
      )(split mustBe empty)
    }

  }

  "the site's navigation" should {

    "list pages that exist" in {
      val config = read("_config.yml").linesIterator.toList
      val start = config.indexWhere(_.startsWith("header_pages:"))
      start must be >= 0
      val listed = config
        .drop(start + 1)
        .takeWhile(_.startsWith("  - "))
        .map(_.drop(4).trim)
      listed must not be empty
      val missing = listed.filterNot(page => new File(root, page).isFile)
      withClue(missing.mkString("\n", "\n", "\n"))(missing mustBe empty)
    }

  }

}

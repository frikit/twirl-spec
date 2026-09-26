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

/** What the documentation tells a reader to copy has to work.
  *
  * The install lines name the version the changelog is about to release, which
  * release.yml in turn holds to the version it publishes, so the two cannot
  * drift apart. And a snippet marked `<!-- compiled: path -->` is the source
  * file at that path, which compiles with the build: an adopter copying the
  * spec base gets code that compiles against this version.
  */
class DocsContentSpec extends AnyWordSpec with Matchers {

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

  private val pages: List[String] =
    "README.md" :: Option(new File(root, "docs").listFiles())
      .map(_.toList)
      .getOrElse(Nil)
      .filter(_.getName.endsWith(".md"))
      .map(f => s"docs/${f.getName}")
      .sorted

  private val newest: String =
    """(?m)^## \[(\d+\.\d+\.\d+)\]""".r
      .findFirstMatchIn(read("CHANGELOG.md"))
      .map(_.group(1))
      .getOrElse(fail("the changelog has no released section"))

  "the install lines" should {

    "name the version the changelog is releasing" in {
      val install =
        """"io\.github\.frikit" %% "twirl-spec[a-z-]*"\s*% "([^"]+)"""".r
      val found = pages.flatMap(page =>
        install.findAllMatchIn(read(page)).map(m => s"$page: ${m.group(1)}")
      )
      found must not be empty
      found.filterNot(_.endsWith(s": $newest")) mustBe empty
    }

    "be backed by a compatibility table for the same major line" in {
      read("README.md") must include(s"| ${newest.takeWhile(_ != '.')}.x | ")
    }
  }

  "a snippet marked as compiled" should {

    "be exactly the source file it names" in {
      val marked = """(?s)<!-- compiled: (\S+) -->\n```scala\n(.*?)```""".r
      val snippets = pages.flatMap(page =>
        marked.findAllMatchIn(read(page)).map(m => (m.group(1), m.group(2)))
      )
      snippets.map(_._1) must contain allOf (
        "all/src/test/scala/snippets/AdoptingSpecBase.scala",
        "all/src/test/scala/snippets/GettingStartedSpecBase.scala"
      )
      snippets.foreach { case (file, code) =>
        read(file).replaceFirst("""(?s)^/\*.*?\*/\n\n""", "") mustBe code
      }
    }
  }
}

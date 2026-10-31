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

import io.github.frikit.twirlspec.html.HtmlChecks
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** Every template under a directory, in one line of a spec. */
class TemplateTreeSpec extends AnyWordSpec with Matchers with HtmlChecks {

  private def tree(files: (String, String)*): Path = {
    val root = Files.createTempDirectory("views")
    files.foreach { case (name, content) =>
      val file = root.resolve(name)
      Files.createDirectories(file.getParent)
      Files.writeString(file, content)
    }
    root
  }

  "haveBalancedTemplates" should {

    "pass on templates that close what they open, at any depth" in {
      val root = tree(
        "a.scala.html" -> "@()\n<p>one</p>",
        "deep/b.scala.html" -> "@()\n<div><p>two</p></div>"
      )
      root must haveBalancedTemplates
      haveBalancedTemplates(root).negatedFailureMessage must include(
        "2 templates"
      )
    }

    "name each problem by its file and line" in {
      val root = tree(
        "good.scala.html" -> "@()\n<p>one</p>",
        "deep/bad.scala.html" -> "@()\n<div>open"
      )
      val result = haveBalancedTemplates(root)
      result.matches mustBe false
      result.failureMessage must include("1 problem(s) in the 2 templates")
      result.failureMessage must include(
        s"${root.resolve("deep/bad.scala.html")}:2 <div> opened on line 2 was never closed"
      )
    }

    "follow a linked directory, inside the tree or as the root itself" in {
      val shared = tree("bad.scala.html" -> "@()\n<div>open")
      val root = tree("good.scala.html" -> "@()\n<p>one</p>")
      Files.createSymbolicLink(root.resolve("shared"), shared)
      haveBalancedTemplates(root).matches mustBe false
      val link = Files.createTempDirectory("links").resolve("views")
      Files.createSymbolicLink(link, shared)
      haveBalancedTemplates(link).failureMessage must
        include("1 problem(s) in the 1 templates")
    }

    "leave other files alone" in {
      tree(
        "a.scala.html" -> "@()\n<p>one</p>",
        "notes.html" -> "<div>open"
      ) must haveBalancedTemplates
    }

    "fail on a directory with no templates, and on one that is not there" in {
      val empty = haveBalancedTemplates(tree("notes.txt" -> "x"))
      empty.matches mustBe false
      empty.failureMessage must include("nothing was checked")
      haveBalancedTemplates(
        Path.of("/definitely/not/here")
      ).matches mustBe false
    }
  }

}

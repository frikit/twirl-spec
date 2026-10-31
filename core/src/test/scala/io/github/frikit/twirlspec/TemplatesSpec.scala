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

import io.github.frikit.twirlspec.render.Templates
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import testviews.html.checkAnswersView

import java.nio.file.{Files, Path}

/** Finding a service's templates, and getting an instance of one. */
class TemplatesSpec extends AnyWordSpec with Matchers with Bilingual {

  private def tree(names: String*): Path = {
    val root = Files.createTempDirectory("views")
    names.map(root.resolve).foreach { file =>
      Files.createDirectories(file.getParent)
      Files.writeString(file, "")
    }
    root
  }

  "Templates.under" should {

    "find every template at any depth, in a stable order, and nothing else" in {
      val root = tree("b.scala.html", "deep/a.scala.html", "notes.html")
      Templates.under(root) mustBe List(
        root.resolve("b.scala.html"),
        root.resolve("deep/a.scala.html")
      )
    }

    "follow a linked directory" in {
      val root = tree("b.scala.html")
      Files.createSymbolicLink(root.resolve("linked"), tree("a.scala.html"))
      Templates.under(root) must contain(root.resolve("linked/a.scala.html"))
    }

    "find none where there is no directory" in {
      Templates.under(Path.of("/definitely/not/here")) mustBe empty
    }
  }

  "templateInstance" should {

    "come from the application" in {
      templateInstance(classOf[checkAnswersView]) mustBe a[checkAnswersView]
    }

    "say what to do on a spec base with no application" in {
      object NoApplication extends TwirlSpecDsl {
        def asked: AnyRef = templateInstance(classOf[checkAnswersView])
      }
      the[IllegalStateException] thrownBy NoApplication.asked must have message
        "testviews.html.checkAnswersView needs an instance and this spec base has no application to ask: override templateInstance"
    }
  }

}

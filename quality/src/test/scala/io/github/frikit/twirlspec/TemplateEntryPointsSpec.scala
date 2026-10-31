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

import io.github.frikit.twirlspec.quality.CoverageChecks
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.Form
import play.api.data.Forms.{text, tuple}

import java.nio.file.{Files, Path}

/** Twirl's entry points in every template under a directory. */
class TemplateEntryPointsSpec
    extends AnyWordSpec
    with Matchers
    with TwirlSpec
    with CoverageChecks {

  /** An empty file for each template: only their names are read. */
  private def tree(names: String*): Path = {
    val root = Files.createTempDirectory("views")
    names.map(name => root.resolve(s"$name.scala.html")).foreach { file =>
      Files.createDirectories(file.getParent)
      Files.writeString(file, "")
    }
    root
  }

  private def failure(result: MatchResult): String = {
    result.matches mustBe false
    result.failureMessage
  }

  "haveAgreeingEntryPoints" should {

    "pass real templates, a class to build or an object" in {
      tree(
        "testviews/checkAnswersView",
        "testviews/dateView",
        "testviews/radioView",
        "testviews/objectView"
      ) must haveAgreeingEntryPoints("Ada", None, messages)
    }

    "build a template whose constructor has a default, not its companion" in {
      tree("testviews/companionView") must
        haveAgreeingEntryPoints("Ada", messages)
    }

    "give apply a repeated parameter as a sequence, and an array as an array" in {
      tree("testviews/repeatedView") must
        haveAgreeingEntryPoints(Array("Ada", "Grace"), messages)
      tree("templateshapes/TakingAnArray") must
        haveAgreeingEntryPoints(Array("Ada"))
    }

    "go by the types render declares, primitives included" in {
      tree("templateshapes/TakingPrimitives") must
        haveAgreeingEntryPoints(1, true)
    }

    "work out a class as Twirl does" in {
      def looksFor(root: Path) = failure(haveAgreeingEntryPoints()(root))
      tree("testviews/deep/nestedView") must
        haveAgreeingEntryPoints("Ada", messages)
      looksFor(tree("views/deep/gone")) must
        include("ClassNotFoundException: views.html.deep.gone")
      looksFor(tree("views/deep/gone").resolve("views")) must
        include("ClassNotFoundException: views.html.deep.gone")
      looksFor(tree("mail/views/deep/gone")) must
        include("ClassNotFoundException: mail.views.html.deep.gone")
      looksFor(tree("mail/deep/gone")) must
        include("ClassNotFoundException: mail.deep.html.gone")
      looksFor(tree("gone")) must include("ClassNotFoundException: html.gone")
    }

    "name a parameter with no stand-in rather than skip it" in {
      val root = tree("testviews/objectView")
      failure(haveAgreeingEntryPoints(messages)(root)) must include(
        s"$root/testviews/objectView.scala.html: NoSuchElementException: no stand-in for a parameter of type java.lang.String"
      )
      failure(
        haveAgreeingEntryPoints(messages)(tree("testviews/repeatedView"))
      ) must include("no stand-in for a parameter of type java.lang.String[]")
    }

    "hold render to what apply gives" in {
      failure(
        haveAgreeingEntryPoints("a")(tree("templateshapes/Disagreeing"))
      ) must include("render produced different html from apply")
    }

    "name a template that throws, and go on to the rest" in {
      val message = failure(
        haveAgreeingEntryPoints("a")(
          tree("templateshapes/Throwing", "templateshapes/Disagreeing")
        )
      )
      message must include("2 problem(s) in the 2 templates")
      message must include("IllegalStateException: boom")
      message must include("render produced different html")
    }

    "name a class with no render" in {
      failure(
        haveAgreeingEntryPoints("a")(tree("templateshapes/Renderless"))
      ) must include("render not found on Renderless")
    }

    "fail on a directory with no templates, or one not there" in {
      failure(haveAgreeingEntryPoints()(tree())) must
        include("nothing was checked")
      failure(haveAgreeingEntryPoints()(Path.of("/definitely/not/here"))) must
        include("nothing was checked")
    }

    "give one template arguments of its own" in {
      val form = Form(tuple("firstName" -> text, "lastName" -> text))
      tree("testviews/nameView", "testviews/objectView") must
        haveAgreeingEntryPoints("Ada", messages)
          .forTemplate("testviews.html.nameView")(form, false, messages)
    }

    "say when those arguments are the wrong number" in {
      failure(
        haveAgreeingEntryPoints()
          .forTemplate("testviews.html.objectView")("a")(
            tree("testviews/objectView")
          )
      ) must include("apply/1 not found on objectView")
    }

    "fail arguments given to a template that is not there" in {
      failure(
        haveAgreeingEntryPoints("Ada", messages)
          .forTemplate("testviews.html.gone")("a")(tree("testviews/objectView"))
      ) must include("testviews.html.gone was given arguments")
    }
  }

}

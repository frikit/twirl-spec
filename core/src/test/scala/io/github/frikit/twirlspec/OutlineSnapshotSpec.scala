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

import io.github.frikit.twirlspec.page.{OutlineSnapshot, Page}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

import scala.collection.mutable

/** A page's outline held to one saved in a file. */
class OutlineSnapshotSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private var updating = false
  private val said = mutable.Buffer.empty[String]

  override def updateOutlineSnapshots: Boolean = updating
  override protected def alertHook(message: String): Unit = said += message

  /** Every part an outline has, once. */
  private val Fixture =
    """<!DOCTYPE html><html lang="en"><head><title>Your name - Example Service - GOV.UK</title></head><body>
      |<header><a class="govuk-header__service-name" href="/">Example Service</a></header>
      |<nav class="hmrc-language-select" aria-label="Language switcher"><a href="?lang=cy" hreflang="cy">Cymraeg</a></nav>
      |<a href="/back" class="govuk-back-link">Back</a>
      |<main>
      |<div class="govuk-error-summary"><h2>There is a problem</h2><ul><li><a href="#firstName">Enter your first name</a></li></ul></div>
      |<span class="govuk-caption-l">Personal details</span>
      |<h1>Your name</h1>
      |<form method="post" action="/name?csrfToken=abc">
      |<input type="hidden" name="csrfToken" value="abc">
      |<label for="firstName">First name</label>
      |<div id="firstName-hint" class="govuk-hint">As on your passport</div>
      |<p id="firstName-error" class="govuk-error-message">Enter your first name</p>
      |<input type="text" id="firstName" name="firstName" value="Ada">
      |<input type="radio" id="email" name="contact" value="email" checked><label for="email">Email</label>
      |<button type="submit">Continue</button>
      |</form>
      |<h2>What happens next</h2>
      |<h3>If we need to contact you</h3>
      |<dl class="govuk-summary-list"><div class="govuk-summary-list__row"><dt>Name</dt><dd>Ada</dd></div></dl>
      |<details class="govuk-details"><summary>Help</summary><p>Call us</p></details>
      |<table><tr><th>Day</th></tr></table>
      |</main></body></html>""".stripMargin

  private def page = Page.fromString(Fixture, english, messages)

  // What a snapshot of the fixture holds. A change that moves it is a minor
  // version, with an Outline entry in the changelog: see Versioning in the README.
  private val Golden =
    """page
      |  title        Your name - Example Service - GOV.UK
      |  lang         en
      |  service      Example Service
      |  h1           Your name
      |  caption      Personal details
      |  back link    /back
      |  lang toggle  present
      |headings
      |    h2  There is a problem
      |  h1  Your name
      |    h2  What happens next
      |      h3  If we need to contact you
      |form POST /name
      |  text     firstName             label "First name"  hint "As on your passport" value=Ada
      |  radio    contact               label "Email" value=email checked
      |  button   ?                      "Continue"
      |components
      |  summary list 1
      |  details      1
      |  table        1
      |errors
      |  summary -> #firstName  Enter your first name
      |  inline  firstName: Enter your first name""".stripMargin

  private def dir = Files.createTempDirectory("outlines")

  "the outline" should {
    "keep the format snapshots are taken in" in {
      page.outline mustBe Golden
    }
  }

  "matchOutlineSnapshot" should {

    "pass on the page's outline, whatever line endings and trailing spaces an editor left" in {
      val file = dir.resolve("name.txt")
      Files.writeString(
        file,
        Golden.linesIterator.map(_ + "  ").mkString("\r\n") + "\r\n\r\n"
      )
      page must matchOutlineSnapshot(file)
    }

    "fail on a missing file, with the page's outline and how to write it" in {
      val file = dir.resolve("missing.txt")
      val result = matchOutlineSnapshot(file)(page)
      result.matches mustBe false
      result.failureMessage must include(
        s"there is no outline snapshot at ${file.toAbsolutePath.normalize}"
      )
      result.failureMessage must include("TWIRLSPEC_UPDATE_SNAPSHOTS=true")
      result.failureMessage must include(Golden)
      Files.exists(file) mustBe false
    }

    "fail on another outline, with the lines that differ" in {
      val file = dir.resolve("name.txt")
      val older = Golden.replace(
        "h1           Your name",
        "h1           Your full name"
      )
      Files.writeString(file, older)
      val result = matchOutlineSnapshot(file)(page)
      result.matches mustBe false
      result.failureMessage must include("(- snapshot, + page)")
      result.failureMessage must include(OutlineSnapshot.diff(older, Golden))
      result.failureMessage must include("-   h1           Your full name")
      result.failureMessage must include("+   h1           Your name")
      result.negatedFailureMessage must include(
        s"the one at ${file.toAbsolutePath.normalize}"
      )
    }

    "write the file when asked, and say so, where it is missing or holds another outline" in {
      val file = dir.resolve("nested").resolve("name.txt")
      said.clear()
      updating = true
      try {
        page must matchOutlineSnapshot(file)
        Files.readString(file) mustBe Golden + "\n"
        Files.writeString(file, "page")
        page must matchOutlineSnapshot(file)
        Files.readString(file) mustBe Golden + "\n"
        page must matchOutlineSnapshot(file)
      } finally updating = false
      // Written beside it and moved over it, with nothing left behind.
      file.getParent.toFile.list.toList mustBe List("name.txt")
      said.toList mustBe List.fill(2)(
        s"wrote the outline snapshot ${file.toAbsolutePath.normalize}"
      )
    }

    "fail, rather than throw, on a file it cannot read or write" in {
      matchOutlineSnapshot(dir)(page).failureMessage must include(
        "could not be read"
      )
      val blocked = Files.createTempFile("outline", ".txt").resolve("name.txt")
      updating = true
      try
        matchOutlineSnapshot(blocked)(page).failureMessage must include(
          "could not be written"
        )
      finally updating = false
    }

    "assert nothing for coverage" in {
      val file = dir.resolve("name.txt")
      Files.writeString(file, Golden)
      val fresh = page
      fresh must matchOutlineSnapshot(file)
      fresh.touchedAnchors mustBe empty
    }
  }

  "the request to write snapshots" should {
    "come from TWIRLSPEC_UPDATE_SNAPSHOTS, set to true or 1" in {
      def asked(env: (String, String)*) =
        OutlineSnapshot.requested(env.toMap.get)
      asked("TWIRLSPEC_UPDATE_SNAPSHOTS" -> "true") mustBe true
      asked("TWIRLSPEC_UPDATE_SNAPSHOTS" -> " TRUE ") mustBe true
      asked("TWIRLSPEC_UPDATE_SNAPSHOTS" -> "1") mustBe true
      asked("TWIRLSPEC_UPDATE_SNAPSHOTS" -> "false") mustBe false
      asked() mustBe false
      (new TwirlSpecDsl {}).updateOutlineSnapshots mustBe
        OutlineSnapshot.requested(sys.env.get)
    }
  }

  "the lines that differ" should {
    "mark what only the snapshot has with -, and what only the page has with +" in {
      OutlineSnapshot.diff("a\nb\nc", "a\nB\nc") mustBe "  a\n- b\n+ B\n  c"
      OutlineSnapshot.diff("a", "a\nb") mustBe "  a\n+ b"
      OutlineSnapshot.diff("a\nb", "a") mustBe "  a\n- b"
      OutlineSnapshot.diff("a\nb\nc\nd", "a\nB\nc\nD") mustBe
        "  a\n- b\n+ B\n  c\n- d\n+ D"
    }
    "list a change too large to line up, rather than run out of memory" in {
      OutlineSnapshot.diff("a\nx\ny\nz", "a\np\nq\nz", mostPairs = 3) mustBe
        "  a\n- x\n- y\n+ p\n+ q\n  z"
      val long = (1 to 3000).map(n => s"  line $n").mkString("\n")
      val diff = OutlineSnapshot.diff(
        long,
        long.replace("line 1500\n", "line 1500 changed\n")
      )
      diff.linesIterator.filter(!_.startsWith("   ")).toList mustBe
        List("-   line 1500", "+   line 1500 changed")
    }
  }

}

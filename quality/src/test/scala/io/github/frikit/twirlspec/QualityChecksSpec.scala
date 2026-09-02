package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.github.frikit.twirlspec.page.Page
import io.github.frikit.twirlspec.quality.{MetadataStandards, PerformanceStandards, QualityChecks, SemanticStandards}

/** Mixing the module in is how a project turns these rules on. */
class QualityChecksSpec extends AnyWordSpec with Matchers with TwirlSpec with QualityChecks {

  private def pageOf(body: String) =
    Page.fromString(
      s"""<!DOCTYPE html><html lang="en"><head><title>t</title><meta charset="utf-8">
         |<meta name="description" content="A page"></head>
         |<body><main><h1>A</h1>$body</main></body></html>""".stripMargin,
      english,
      messages
    )

  "mixing in QualityChecks" should {

    "add every rule in the module to the active set" in {
      val ids = standardsRules.map(_.id)
      ids must contain allElementsOf SemanticStandards.all.map(_.id)
      ids must contain allElementsOf PerformanceStandards.all.map(_.id)
      ids must contain allElementsOf MetadataStandards.all.map(_.id)
    }

    "make display fail on markup only these rules object to" in {
      // A nested control: nothing in the accessibility or safety sets sees it.
      a[org.scalatest.exceptions.TestFailedException] must be thrownBy {
        pageOf("""<a href="/x">Read <button>more</button></a>""") must display(heading(literal("A")))
      }
    }

    "leave clean markup alone" in {
      pageOf("""<p>Some words</p>""") must display(heading(literal("A")))
    }
  }

}

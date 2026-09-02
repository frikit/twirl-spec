package io.github.frikit.twirlspec

import org.scalatest.{Alerting, Suite}
import play.api.i18n.Lang

/** The test fixtures come in English and Welsh, so the suites that use both say so. */
trait Bilingual extends TwirlSpec { self: Suite with Alerting =>

  val welsh: Lang = Lang("cy")

  override def applicationConfig: Map[String, Any] =
    super.applicationConfig + ("play.i18n.langs" -> Seq("en", "cy"))

}

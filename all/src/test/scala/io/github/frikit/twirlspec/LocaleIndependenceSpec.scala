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

import io.github.frikit.twirlspec.aria.AriaStandards
import io.github.frikit.twirlspec.page.Page
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.Files
import java.util.Locale
import scala.jdk.CollectionConverters._
import scala.util.Using

/** A page reads the same on every machine. Under a Turkish default locale `I`
  * lower-cases to a dotless `ı`, so a case conversion in the default locale
  * misreads `DIALOG`, `NOINDEX` or `HIDDEN`.
  */
class LocaleIndependenceSpec extends AnyWordSpec with Matchers with TwirlSpec {

  private val root = Iterator
    .iterate(new File(sys.props("user.dir")).getAbsoluteFile)(_.getParentFile)
    .takeWhile(_ != null)
    .find(dir => new File(dir, "README.md").isFile)
    .getOrElse(sys.error("no README.md above the working directory"))

  "The library" should {

    "change case only in a locale it names" in {
      // with no locale, or empty parentheses, the conversion uses the default
      val defaultLocale = """\.to(Lower|Upper)Case(?!\s*\(\s*[^)\s])""".r
      val offenders = for {
        module <- root.listFiles.toList.sortBy(_.getName)
        main = new File(module, "src/main")
        if main.isDirectory
        file <- Using.resource(Files.walk(main.toPath))(
          _.iterator.asScala.toList
        )
        if file.toString.endsWith(".scala")
        (line, n) <- Files.readAllLines(file).asScala.toList.zipWithIndex
        if defaultLocale.findFirstIn(line).nonEmpty
      } yield s"${root.toPath.relativize(file)}:${n + 1}"
      offenders mustBe empty
    }

    "read a role in capitals under a Turkish default locale" in {
      val default = Locale.getDefault
      try {
        Locale.setDefault(Locale.forLanguageTag("tr"))
        val page = Page.fromString(
          """<!DOCTYPE html><html lang="en"><head><title>t</title></head>
            |<body><main><h1>A</h1><div role="DIALOG" aria-label="Help">x</div></main></body></html>""".stripMargin,
          english,
          messages
        )
        AriaStandards.all.flatMap(_.check(page)).map(_.rule) must
          not contain "aria-role-is-real"
      } finally Locale.setDefault(default)
    }
  }

}

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

import sbt.*

import scala.xml.{Elem, XML}

/** Holds the published POMs to the library's dependency decisions. Run after
  * `makePom`, for the version the POMs were made for:
  *
  * {{{
  * sbt 'set ThisBuild / version := "0.0.0-CONSUMER"' publishLocal makePom checkPoms
  * }}}
  *
  * Each module's POM must ask a consumer for Play as `provided`, so the library
  * never moves a service's Play; for twirl-api as a normal dependency at 2.0.1,
  * the one Play 3.0.0 ships; for Scala on the 3.3 LTS line, which every later
  * Scala 3 can read; and for nothing that was only needed to test the library.
  */
object CheckPoms {

  private val Modules = 9
  private val Play = "3.0.0"
  private val Provided =
    Set("play_3", "play-test_3", "play-guice_3", "play-filters-helpers_3")

  /** What every module's POM must declare, and what the core's must declare as
    * well: (group, artifact) -> (scope, version, or None for any version).
    */
  private val EveryModule = Map(
    ("org.playframework", "play_3") -> ("provided", Some(Play)),
    ("org.playframework", "play-test_3") -> ("provided", Some(Play)),
    ("org.playframework", "play-guice_3") -> ("provided", Some(Play)),
    ("org.scalatest", "scalatest_3") -> ("compile", None),
    // the version is held to the 3.3 line below
    ("org.scala-lang", "scala3-library_3") -> ("compile", None)
  )
  private val CoreOnly = Map(
    ("org.playframework", "play-filters-helpers_3") -> ("provided", Some(Play)),
    ("org.playframework.twirl", "twirl-api_3") -> ("compile", Some("2.0.1")),
    ("org.jsoup", "jsoup") -> ("compile", None)
  )

  private def problemsIn(pom: File): Seq[String] = {
    val root: Elem = XML.loadFile(pom)
    val declared = (root \ "dependencies" \ "dependency").map { d =>
      val scope =
        Some((d \ "scope").text).filter(_.nonEmpty).getOrElse("compile")
      ((d \ "groupId").text, (d \ "artifactId").text) -> (
        scope,
        (d \ "version").text
      )
    }
    val wrong = declared.flatMap { case ((group, artifact), (scope, version)) =>
      val name = s"$group:$artifact:$version ($scope)"
      Seq(
        Some(s"$name should be provided at $Play").filter(_ =>
          group == "org.playframework" && Provided(artifact) &&
            (scope != "provided" || version != Play)
        ),
        Some(s"$name should be on the 3.3 LTS line").filter(_ =>
          group == "org.scala-lang" && artifact == "scala3-library_3" &&
            !version.startsWith("3.3.")
        ),
        Some(s"$name is a test dependency in a published POM")
          .filter(_ => scope == "test")
      ).flatten
    }
    val required =
      if (pom.getName.startsWith("twirl-spec-core_3-")) EveryModule ++ CoreOnly
      else EveryModule
    val found = declared.toMap
    val missing = required.toSeq.sortBy(_._1).flatMap {
      case ((group, artifact), (scope, version)) =>
        found.get((group, artifact)) match {
          case None => Some(s"$group:$artifact is missing")
          case Some((s, v)) if s != scope || version.exists(_ != v) =>
            Some(
              s"$group:$artifact is $v ($s), should be ${version.getOrElse("any")} ($scope)"
            )
          case _ => None
        }
    }
    wrong ++ missing
  }

  def apply(base: File, version: String, log: Logger): Unit = {
    val poms = Option(base.listFiles).toList.flatten
      .flatMap(m => Option((m / "target").listFiles).toList.flatten)
      .filter(_.getName.startsWith("scala-"))
      .flatMap(d => Option(d.listFiles).toList.flatten)
      .filter(_.getName.endsWith(s"-$version.pom"))
      .sortBy(_.getPath)
    val count =
      if (poms.size == Modules) Nil
      else
        List(s"expected $Modules module POMs for $version, found ${poms.size}")
    val problems =
      count ++ poms.flatMap(p =>
        problemsIn(p).map(q => s"${base.toPath.relativize(p.toPath)}: $q")
      )
    if (problems.nonEmpty) {
      problems.foreach(log.error(_))
      sys.error(s"${problems.size} problem(s) in the POMs")
    }
    log.info(s"${poms.size} POMs hold to the dependency decisions")
  }

}

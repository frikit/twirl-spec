// Scala 3 only, on the LTS line: a consumer on any later Scala 3 can read what this produces.
val scala3 = "3.3.8"

ThisBuild / organization := "io.github.frikit"
ThisBuild / organizationName := "frikit"
ThisBuild / homepage := Some(url("https://github.com/frikit/twirl-spec"))

ThisBuild / licenses := Seq(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
)

ThisBuild / developers := List(
  Developer(
    "frikit",
    "Victor Osipov",
    "osipovvictor1994@gmail.com",
    url("https://github.com/frikit")
  )
)

// The version is the git tag, via sbt-ci-release's dynver: v1.2.3 publishes 1.2.3.
// Every push to main is tagged and released by .github/workflows/release.yml, to
// the Sonatype Central Portal, which sbt-ci-release targets by default.
ThisBuild / scalaVersion := scala3
ThisBuild / versionScheme := Some("early-semver")

// An unused import is an error, not a warning that scrolls past.
ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-release",
  "21",
  "-Wunused:imports",
  "-Wconf:msg=unused import:e"
)

// Every source file carries this header: `headerCreateAll` adds it to a new
// file, and `headerCheckAll` fails the build when one is missing. sbt-header
// reads the setting per project, so it is applied to each one rather than
// to ThisBuild.
lazy val licenceHeader =
  headerLicense := Some(HeaderLicense.ALv2("2026", "frikiT"))

lazy val commonSettings = Seq(
  licenceHeader,
  // Play and Twirl sit on the oldest 3.0.x on purpose, and Scala on the LTS
  // line, so dependencyUpdates should not keep proposing the newest of each.
  dependencyUpdatesFilter -= moduleFilter(organization = "org.playframework"),
  dependencyUpdatesFilter -= moduleFilter(organization = "org.playframework.twirl"),
  dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang"),
  // Suites share one Play application through SharedApplication, and one suite
  // exercises the shutdown hook that stops it, so suites run one at a time.
  // This only serialises the suites within a module; the restriction below
  // does the same for the modules.
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
)

// Tests are not forked, so without this the modules' test tasks would run
// concurrently in the one JVM that holds the shared application.
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

// Point git at the hooks in this repository, so a fresh clone gets the pre-push
// check by running sbt once rather than by remembering a setup step.
Global / onLoad := {
  import scala.sys.process._
  val installHooks = (state: State) => {
    if (file(".git").exists() && "git config core.hooksPath .githooks".! != 0)
      state.log.warn(
        "could not set core.hooksPath; the pre-push check is not installed"
      )
    state
  }
  installHooks compose (Global / onLoad).value
}

lazy val root = Project("twirl-spec", file("."))
  .settings(name := "twirl-spec", publish / skip := true, licenceHeader)
  .aggregate(core, wcag, govuk, quality, messages, i18n, aria, html, all)

/** The page model, the expectation DSL and the ScalaTest matchers. Carries no
  * rules of its own, so it never judges a page against a design system.
  */
lazy val core = Project("twirl-spec-core", file("core"))
  .enablePlugins(SbtTwirl)
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(
    libraryDependencies ++= LibDependencies.core,
    description := "Page model, expectation DSL and ScalaTest matchers for testing Twirl views.",
    // The twirl-api this module depends on is the one Play 3.0.0 ships, so the
    // POM asks a consumer for nothing newer than their own Play already brings.
    TwirlKeys.twirlVersion := "2.0.1",
    // Twirl fixtures for the test suites, shared with the other modules via
    // test->test. They declare exactly the import every fixture uses, in place
    // of Twirl's defaults (Html, Txt, Xml, JavaScript and the helper objects),
    // which these HTML fixtures never touch and which would otherwise fail the
    // unused-import check. A template that needs more imports it itself.
    Test / TwirlKeys.templateImports := Seq("play.api.i18n.Messages")
  )

/** Accessibility and Twirl rendering rules that apply to any page. */
lazy val wcag = Project("twirl-spec-wcag", file("wcag"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(
    description := "WCAG accessibility and Twirl rendering rules for twirl-spec."
  )

/** GOV.UK Design System conventions, on top of the accessibility rules. */
lazy val govuk = Project("twirl-spec-govuk", file("govuk"))
  .dependsOn(core % "compile->compile;test->test", wcag)
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(description := "GOV.UK Design System rules for twirl-spec.")

/** Checks that are neither accessibility, safety nor design system: semantics,
  * page weight and metadata.
  */
lazy val quality = Project("twirl-spec-quality", file("quality"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(
    description := "Semantic, performance and metadata rules for twirl-spec."
  )

/** Message-file integrity checks. */
lazy val messages = Project("twirl-spec-messages", file("messages"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.messages)
  .settings(description := "Message-file integrity checks for twirl-spec.")

/** Whether a view says the same thing in every language it is offered in. */
lazy val i18n = Project("twirl-spec-i18n", file("i18n"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(description := "Language parity rules for twirl-spec.")

/** The static half of what an automated accessibility tool reports. */
lazy val aria = Project("twirl-spec-aria", file("aria"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(
    description := "ARIA correctness rules for twirl-spec, without a browser."
  )

/** Basic HTML validity, read from the source rather than the repaired tree. */
lazy val html = Project("twirl-spec-html", file("html"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(description := "HTML validity rules for twirl-spec.")

/** Everything, for a project that would rather add one dependency than eight.
  *
  * Carries no rules of its own; it exists to pull the others in and to offer
  * `AllChecks`, which mixes in every rule module at once.
  */
lazy val all = Project("twirl-spec-all", file("all"))
  .dependsOn(
    core % "compile->compile;test->test",
    wcag,
    govuk,
    quality,
    messages,
    i18n,
    aria,
    html
  )
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(description := "All of twirl-spec in one dependency.")

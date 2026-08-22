val scala2_13 = "2.13.18"
val scala3    = "3.3.7" // LTS: binary-compatible with every later Scala 3 release

ThisBuild / organization := "io.github.frikit"
ThisBuild / organizationName := "frikit"
ThisBuild / homepage := Some(url("https://github.com/frikit/twirl-spec"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(Developer("frikit", "Victor O", "", url("https://github.com/frikit")))
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala2_13
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-release", "21")

lazy val commonSettings = Seq(
  crossScalaVersions := Seq(scala2_13, scala3),
  // Suites share one Play application through SharedApplication, so they run
  // one at a time. Running them in parallel would let one suite stop an
  // application another is still using.
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
)

// Point git at the hooks in this repository, so a fresh clone gets the pre-push
// check by running sbt once rather than by remembering a setup step.
Global / onLoad := {
  import scala.sys.process._
  val installHooks = (state: State) => {
    if (file(".git").exists() && "git config core.hooksPath .githooks".! != 0)
      state.log.warn("could not set core.hooksPath; the pre-push check is not installed")
    state
  }
  installHooks compose (Global / onLoad).value
}

lazy val root = Project("twirl-spec", file("."))
  .settings(name := "twirl-spec", publish / skip := true)
  .aggregate(core, wcag, govuk, quality, messages)

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
    // Twirl fixtures for the test suites, shared with the other modules via test->test.
    Test / TwirlKeys.templateImports ++= Seq(
      "play.api.i18n.Messages",
      "play.api.mvc.RequestHeader",
      "play.api.data.Form"
    )
  )

/** Accessibility and Twirl rendering rules that apply to any page. */
lazy val wcag = Project("twirl-spec-wcag", file("wcag"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.rules)
  .settings(description := "WCAG accessibility and Twirl rendering rules for twirl-spec.")

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
  .settings(description := "Semantic, performance and metadata rules for twirl-spec.")

/** Message-file integrity checks. */
lazy val messages = Project("twirl-spec-messages", file("messages"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(CodeCoverageSettings())
  .settings(libraryDependencies ++= LibDependencies.messages)
  .settings(description := "Message-file integrity checks for twirl-spec.")

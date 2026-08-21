val scala2_13 = "2.13.18"
val scala3    = "3.3.7" // LTS: binary-compatible with every later Scala 3 release

ThisBuild / organization := "io.github.frikit"
ThisBuild / organizationName := "frikit"
ThisBuild / homepage := Some(url("https://github.com/frikit/twirl-spec"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / developers := List(
  Developer("frikit", "Victor O", "", url("https://github.com/frikit"))
)

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala2_13
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-release", "21")

lazy val library = Project("twirl-spec", file("."))
  .enablePlugins(SbtTwirl)
  .settings(CodeCoverageSettings())
  .settings(
    crossScalaVersions := Seq(scala2_13, scala3),
    libraryDependencies ++= LibDependencies(),
    description := "A ScalaTest toolkit for testing Twirl views: an expectation DSL, " +
      "accessibility and structural rules, and message-file checks.",
    // Twirl fixtures for this library's own tests.
    Test / TwirlKeys.templateImports ++= Seq(
      "play.api.i18n.Messages",
      "play.api.mvc.RequestHeader",
      "play.api.data.Form"
    ),
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
  )

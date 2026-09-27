// A service's view specs against twirl-spec as a service gets it: the artifacts
// `publishLocal` made, on the Scala, `-release` and Play a service builds with.
// CI runs it on the combinations in .github/workflows/ci.yml; locally:
//
//   sbt 'set ThisBuild / version := "0.0.0-CONSUMER"' publishLocal
//   cd consumer && sbt test
val twirlSpecVersion = sys.props.getOrElse("twirlspec.version", "2.2.0")

ThisBuild / scalaVersion := sys.props.getOrElse("consumer.scala", "3.3.7")
// Many service builds compile with -release 11 unless they say otherwise.
ThisBuild / scalacOptions ++= Seq(
  "-release",
  sys.props.getOrElse("consumer.release", "11")
)

val specDependencies = Seq(
  guice,
  "org.playframework" %% "play-test" % play.core.PlayVersion.current % Test,
  "io.github.frikit" %% "twirl-spec-all" % twirlSpecVersion % Test
)

/** A Play application built the usual way, with the filters. */
lazy val service = (project in file("service"))
  .enablePlugins(PlayScala)
  .settings(libraryDependencies ++= specDependencies)

/** A Play application built without the filters, and so without
  * play-filters-helpers: TwirlSpec has to work there too, without a token.
  */
lazy val nofilters = (project in file("nofilters"))
  .enablePlugins(PlayScala)
  .disablePlugins(play.sbt.PlayFilters)
  .settings(libraryDependencies ++= specDependencies)

lazy val root = (project in file("."))
  .aggregate(service, nofilters)
  .settings(publish / skip := true)

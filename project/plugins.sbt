// Everything this build needs, declared here rather than in ~/.sbt, and all of
// it from Maven Central, so the library resolves and builds anywhere.
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.9")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.7.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

// One Scaladoc site across every module, for the documentation site.
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

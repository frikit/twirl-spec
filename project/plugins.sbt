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

// Hold each release to the promise that the public API is stable within a
// major version. sbt-tasty-mima would add Scala 3's TASTy on top, but it builds
// the previous release's classpath from its POM, where Play is Provided and so
// absent, and every symbol that mentions Play then fails as an internal error.
// The library has no inline members, which is what TASTy checks add most for.
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.2.1")

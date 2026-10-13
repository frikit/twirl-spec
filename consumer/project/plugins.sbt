// The Play a service builds with. The default is the newest, which Scala
// Steward keeps current; CI also runs this build on the Play of a service
// today, which it passes as consumer.play.
addSbtPlugin(
  "org.playframework" % "sbt-plugin" % sys.props
    .getOrElse("consumer.play", "3.0.12")
)

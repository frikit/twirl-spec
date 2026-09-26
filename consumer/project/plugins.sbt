// The Play a service builds with; CI runs this build on more than one.
addSbtPlugin(
  "org.playframework" % "sbt-plugin" % sys.props.getOrElse("consumer.play", "3.0.11")
)

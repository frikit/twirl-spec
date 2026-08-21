import sbt.*

object LibDependencies {

  // Compiled against the oldest supported Play on purpose: `Provided` means the consumer supplies its own, and bui
  private val playVersion      = "3.0.10"
  private val jsoupVersion     = "1.23.1"
  private val scalatestVersion = "3.2.20"

  /** Provided dependencies are not transitive, so every module that touches Play or ScalaTest types has to declare them, not just the core.
    */
  val shared: Seq[ModuleID] = Seq(
    "org.playframework" %% "play"       % playVersion % Provided,
    "org.playframework" %% "play-test"  % playVersion % Provided,
    "org.playframework" %% "play-guice" % playVersion % Provided,
    "org.scalatest"     %% "scalatest"  % scalatestVersion
  )

  val core: Seq[ModuleID] = shared :+ ("org.jsoup" % "jsoup" % jsoupVersion)

  val rules: Seq[ModuleID]    = shared
  val messages: Seq[ModuleID] = shared

}

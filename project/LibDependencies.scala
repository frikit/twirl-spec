import sbt.*

object LibDependencies {

  // Compiled against the oldest supported Play on purpose: `Provided` means the
  // consumer supplies its own, and building against the oldest is what keeps
  // this library usable across the range.
  private val playVersion      = "3.0.10"
  private val jsoupVersion     = "1.23.1"
  private val scalatestVersion = "3.2.20"

  private val compile: Seq[ModuleID] = Seq(
    // Play is always present in a consuming frontend. Keeping it Provided means
    // this library never dictates the consumer's Play patch version.
    "org.playframework" %% "play"       % playVersion % Provided,
    "org.playframework" %% "play-test"  % playVersion % Provided,
    "org.playframework" %% "play-guice" % playVersion % Provided,
    // The only two dependencies this library actually carries.
    "org.jsoup"          % "jsoup"      % jsoupVersion,
    "org.scalatest"     %% "scalatest"  % scalatestVersion
  )

  private val test: Seq[ModuleID] = Seq.empty

  def apply(): Seq[ModuleID] = compile ++ test

}

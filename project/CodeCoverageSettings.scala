import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  /** Thresholds are per module, and set just under what each module actually
    * reaches. A single global number does not work once the build is split: a
    * small module of pure rule definitions has a very different shape from the
    * core.
    */
  def apply(statement: Int = 95, branch: Int = 90): Seq[Setting[?]] = Seq(
    coverageExcludedPackages := "<empty>;Reverse.*;..*Routes.*;",
    coverageMinimumStmtTotal := statement,
    coverageMinimumBranchTotal := branch,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

}

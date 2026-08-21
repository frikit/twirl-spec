import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  /** Every module is at 100% statement and branch, and the gate is set there so it stays that way: a new branch has to be covered, excluded with a `$COVERAGE-OFF$` marker and a reason, or deliberately dropped by lowering this number in a commit someone can see.
    */
  def apply(statement: Int = 100, branch: Int = 100): Seq[Setting[?]] = Seq(
    coverageExcludedPackages := "<empty>;Reverse.*;..*Routes.*;",
    coverageMinimumStmtTotal := statement,
    coverageMinimumBranchTotal := branch,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

}

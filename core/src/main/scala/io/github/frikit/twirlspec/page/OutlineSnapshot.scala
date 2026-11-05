/*
 * Copyright 2026 frikiT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.frikit.twirlspec.page

import java.nio.file.{Files, Path, StandardCopyOption}

import scala.util.control.NonFatal

/** A page's outline held to the one saved in a file. */
private[twirlspec] object OutlineSnapshot {

  /** What asks for snapshots to be written rather than compared. */
  val UpdateVariable = "TWIRLSPEC_UPDATE_SNAPSHOTS"

  /** Whether an environment asks for snapshots to be written: `true` or `1`. */
  def requested(env: String => Option[String]): Boolean =
    env(UpdateVariable)
      .map(_.trim.toLowerCase)
      .exists(v => v == "true" || v == "1")

  /** What came of a comparison: whether it holds, what to say if it does not,
    * and the file written, if one was.
    */
  final case class Comparison(
      matches: Boolean,
      message: String,
      wrote: Option[Path]
  )

  /** The outline against the file, or, when `update` is on and the file is
    * missing or holds another outline, the outline written to it.
    */
  def compare(outline: String, file: Path, update: Boolean): Comparison = {
    val path = file.toAbsolutePath.normalize
    val page = normalised(outline)
    val saved =
      try
        Right(
          Option.when(Files.exists(path))(normalised(Files.readString(path)))
        )
      catch { case NonFatal(e) => Left(e) }
    saved match {
      case Left(e) =>
        Comparison(
          false,
          s"the outline snapshot at $path could not be read: $e",
          None
        )
      case Right(Some(`page`)) => Comparison(true, "", None)
      case Right(_) if update  => write(path, page)
      case Right(None)         =>
        Comparison(
          false,
          s"there is no outline snapshot at $path. If this outline of the page is right, " +
            s"run with $UpdateVariable=true to write it:\n$page",
          None
        )
      case Right(Some(snapshot)) =>
        Comparison(
          false,
          s"the page's outline differs from the snapshot at $path (- snapshot, + page):\n" +
            s"${diff(snapshot, page)}\n" +
            s"If the page is right, run with $UpdateVariable=true to write the snapshot again.",
          None
        )
    }
  }

  /** Written beside the snapshot first and then moved over it in one step, so a
    * write that fails leaves the old snapshot whole.
    */
  private def write(path: Path, outline: String): Comparison =
    try {
      Files.createDirectories(path.getParent)
      val written =
        Files.createTempFile(path.getParent, s"${path.getFileName}.", ".tmp")
      try {
        Files.writeString(written, outline + "\n")
        Files.move(written, path, StandardCopyOption.ATOMIC_MOVE)
      } finally Files.deleteIfExists(written)
      Comparison(true, "", Some(path))
    } catch {
      case NonFatal(e) =>
        Comparison(
          false,
          s"the outline snapshot at $path could not be written: $e",
          None
        )
    }

  /** Line endings, and spaces at the ends of lines and of the file, as an
    * editor may leave them, do not count.
    */
  private def normalised(text: String): String =
    text
      .replace("\r\n", "\n")
      .linesIterator
      .map(_.replaceAll("\\s+$", ""))
      .mkString("\n")
      .replaceAll("\n+$", "")

  /** The most pairs of lines `diff` lines up, a table of four bytes a pair. */
  val MostPairs: Long = 4000000L

  /** Two outlines a line at a time: a line only the snapshot has is marked `-`,
    * a line only the page has `+`, and a line both have is indented. What both
    * begin and end with is never lined up, so a small change to a long outline
    * costs little; a change between more than `mostPairs` pairs of lines is
    * listed, the snapshot's lines and then the page's, without lining them up.
    */
  def diff(
      snapshot: String,
      page: String,
      mostPairs: Long = MostPairs
  ): String = {
    val a = snapshot.split("\n", -1).toVector
    val b = page.split("\n", -1).toVector
    val start = a.zip(b).takeWhile { case (x, y) => x == y }.size
    val end = a
      .drop(start)
      .reverseIterator
      .zip(b.drop(start).reverseIterator)
      .takeWhile { case (x, y) => x == y }
      .size
    val (was, now) =
      (a.slice(start, a.size - end), b.slice(start, b.size - end))
    val changed =
      if (was.size.toLong * now.size > mostPairs)
        was.map(line => s"- $line") ++ now.map(line => s"+ $line")
      else linedUp(was, now)
    (a.take(start).map(line => s"  $line") ++ changed ++
      a.takeRight(end).map(line => s"  $line")).mkString("\n")
  }

  /** Two runs of lines lined up by the longest run of lines both share. */
  private def linedUp(a: Vector[String], b: Vector[String]): Vector[String] = {
    // How many lines both share from each pair of positions on.
    val common = Array.ofDim[Int](a.size + 1, b.size + 1)
    for (i <- a.indices.reverse; j <- b.indices.reverse)
      common(i)(j) =
        if (a(i) == b(j)) common(i + 1)(j + 1) + 1
        else math.max(common(i + 1)(j), common(i)(j + 1))
    val lines = Vector.newBuilder[String]
    var i = 0
    var j = 0
    while (i < a.size || j < b.size)
      if (i < a.size && j < b.size && a(i) == b(j)) {
        lines += s"  ${a(i)}"
        i += 1
        j += 1
      } else if (
        i < a.size && (j == b.size || common(i + 1)(j) >= common(i)(j + 1))
      ) {
        lines += s"- ${a(i)}"
        i += 1
      } else {
        lines += s"+ ${b(j)}"
        j += 1
      }
    lines.result()
  }

}

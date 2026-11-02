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

package io.github.frikit.twirlspec.render

import java.nio.file.{FileVisitOption, Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

/** A service's Twirl templates, as files. */
private[twirlspec] object Templates {

  /** Every template under this directory, in a stable order, or none where it
    * is not a directory.
    */
  def under(root: Path): List[Path] =
    if (!Files.isDirectory(root)) Nil
    else
      Using.resource(Files.walk(root, FileVisitOption.FOLLOW_LINKS))(
        _.iterator.asScala
          .filter(_.toString.endsWith(".scala.html"))
          .toList
          .sortBy(_.toString)
      )

}

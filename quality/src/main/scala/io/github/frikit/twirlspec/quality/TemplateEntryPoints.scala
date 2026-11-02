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

package io.github.frikit.twirlspec.quality

import io.github.frikit.twirlspec.render.Templates
import org.scalatest.matchers.{MatchResult, Matcher}
import play.twirl.api.BaseScalaTemplate

import java.lang.invoke.MethodType
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Twirl's generated `render`, `f` and `ref` agree with `apply`, in every
  * template under a directory: one Twirl compiles from, such as `app` or
  * `src/main/twirl`, or the `views` directory inside it.
  *
  * A template's arguments are the first stand-in of each type `render`
  * declares, so one taking a type with no stand-in fails, by name, rather than
  * being skipped. `Form[A]` and `Form[B]` are one class at runtime, so a
  * template reading its form's value can be handed the wrong one: `forTemplate`
  * gives it arguments of its own.
  */
final class TemplateEntryPoints private[quality] (
    standIns: Seq[Any],
    arguments: Map[String, Seq[Any]],
    loader: ClassLoader,
    instanceOf: Class[_] => AnyRef
) extends Matcher[Path] {

  /** These arguments for one template, named by the class it compiles to. They
    * are the ones `render` takes: every parameter including the implicit ones,
    * flattened, in order.
    */
  def forTemplate(className: String)(args: Any*): TemplateEntryPoints =
    new TemplateEntryPoints(
      standIns,
      arguments + (className -> args.toSeq),
      loader,
      instanceOf
    )

  def apply(root: Path): MatchResult = {
    val templates = Templates.under(root).map(t => t -> compiledName(root, t))
    val misnamed = (arguments.keySet -- templates.map(_._2)).toList.sorted
      .map(n =>
        s"$n was given arguments, but nothing under $root compiles to it"
      )
    val problems = misnamed ++ templates.flatMap { case (template, name) =>
      Try(problemsWith(name))
        .fold(e => List(EntryPoints.rootCause(e)), identity)
        .map(problem => s"$template: $problem")
    }
    MatchResult(
      templates.nonEmpty && problems.isEmpty,
      if (templates.isEmpty)
        s"no Twirl templates were found under $root, so nothing was checked"
      else
        s"${problems.size} problem(s) in the ${templates.size} templates under $root:\n  " +
          problems.mkString("\n  "),
      s"the ${templates.size} templates under $root all agreed with apply, but were expected not to"
    )
  }

  private def problemsWith(name: String): List[String] = {
    val view = instance(name)
    EntryPoints.problemsAgainstApply(
      view,
      arguments.getOrElse(name, EntryPoints.parametersOf(view).map(standIn))
    )
  }

  private def standIn(parameter: Class[_]): Any = {
    val wanted = MethodType.methodType(parameter).wrap().returnType()
    standIns
      .find(wanted.isInstance)
      .getOrElse(
        throw new NoSuchElementException(
          s"no stand-in for a parameter of type ${parameter.getTypeName}"
        )
      )
  }

  /** A template with no `@this` compiles to an object. One with it compiles to
    * a class to build, whose companion, if a default on its constructor gave it
    * one, is not the template.
    */
  private def instance(name: String): AnyRef =
    Try(load(s"$name$$").getField("MODULE$").get(null))
      .filter(_.isInstanceOf[BaseScalaTemplate[_, _]])
      .getOrElse(instanceOf(load(name)))

  private def load(name: String): Class[_] = Class.forName(name, true, loader)

  /** The class Twirl compiles a template to: `html` follows a directory called
    * `views`, and with none comes just before the template's own name.
    */
  private def compiledName(root: Path, template: Path): String = {
    val relative = root.relativize(template).asScala.map(_.toString).toList
    val parts = if (root.endsWith("views")) "views" :: relative else relative
    val packages =
      if (parts.init.contains("views")) parts.init.flatMap {
        case "views" => List("views", "html")
        case folder  => List(folder)
      }
      else parts.init :+ "html"
    (packages :+ parts.last.stripSuffix(".scala.html")).mkString(".")
  }

}

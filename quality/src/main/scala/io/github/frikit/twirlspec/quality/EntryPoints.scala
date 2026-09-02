/*
 * Copyright 2026 Victor Osipov
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

import io.github.frikit.twirlspec.standards._

import play.twirl.api.Html

/** Twirl gives every template three entry points besides `apply`: `render` for
  * Java callers, `f` for the curried function form, and `ref`.
  *
  * Nothing in a normal service calls them, so a change to a template's
  * parameters can break them with no test noticing, and they sit in coverage
  * reports as permanently unreached lines. They are reached by reflection here
  * so a spec does not have to spell out each template's arity by hand.
  */
object EntryPoints {

  /** Whatever went wrong; empty means all three agree with `apply`. */
  def problems(view: AnyRef, expected: Html, args: Seq[Any]): List[String] =
    List(checkRender(view, expected, args), checkF(view, expected, args), checkRef(view)).flatten

  private def checkRender(view: AnyRef, expected: Html, args: Seq[Any]): Option[String] =
    method(view, "render", args.size) match {
      case None    => Some(s"render/${args.size} not found on ${name(view)}")
      case Some(m) =>
        attempt("render")(m.invoke(view, args.map(_.asInstanceOf[AnyRef]): _*)).flatMap(sameAs(expected, "render"))
    }

  private def checkF(view: AnyRef, expected: Html, args: Seq[Any]): Option[String] =
    method(view, "f", 0) match {
      case None    => Some(s"f not found on ${name(view)}")
      case Some(m) => attempt("f")(applyCurried(m.invoke(view), args)).flatMap(sameAs(expected, "f"))
    }

  private def checkRef(view: AnyRef): Option[String] =
    method(view, "ref", 0) match {
      case None    => Some(s"ref not found on ${name(view)}")
      case Some(m) =>
        val got = m.invoke(view)
        if (got eq view) None else Some(s"ref returned ${name(got)} rather than the template itself")
    }

  /** `f` is curried the same way the template's parameter lists are, so the arguments are fed in a group at a time. */
  private def applyCurried(f: AnyRef, args: Seq[Any]): AnyRef = {
    var current   = f
    var remaining = args
    var arity     = functionArity(current)
    while (arity.isDefined) {
      val wanted       = arity.get
      val (now, later) = remaining.splitAt(wanted)
      if (now.size < wanted) throw new IllegalStateException(s"f wanted $wanted more arguments, ${now.size} left")
      current = invokeFunction(current, now)
      remaining = later
      arity = functionArity(current)
    }
    current
  }

  private def functionArity(o: AnyRef): Option[Int] = (0 to 22).find(n => functionClass(n).isInstance(o))

  private def functionClass(n: Int): Class[_] = Class.forName(s"scala.Function$n")

  private def invokeFunction(f: AnyRef, args: Seq[Any]): AnyRef = {
    val m = functionClass(args.size).getMethods.find(_.getName == "apply").get
    m.invoke(f, args.map(_.asInstanceOf[AnyRef]): _*)
  }

  private def method(view: AnyRef, named: String, arity: Int) =
    view.getClass.getMethods.find(m => m.getName == named && m.getParameterCount == arity)

  private def attempt(what: String)(f: => AnyRef): Option[AnyRef] =
    try Some(f)
    catch { case e: Throwable => throw new AssertionError(s"$what threw ${rootCause(e)}", e) }

  private def sameAs(expected: Html, what: String)(got: AnyRef): Option[String] =
    if (got.toString == expected.toString) None
    else Some(s"$what produced different html from apply")

  private def rootCause(e: Throwable): String =
    Option(e.getCause).map(rootCause).getOrElse(s"${e.getClass.getSimpleName}: ${e.getMessage}")

  private def name(o: AnyRef): String = o.getClass.getSimpleName.stripSuffix("$")
}

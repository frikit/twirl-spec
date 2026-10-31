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

import play.twirl.api.Html

import java.lang.reflect.{Method, Modifier}
import scala.collection.immutable.ArraySeq
import scala.runtime.FunctionXXL
import scala.util.control.NonFatal

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
    List(
      checkRender(view, expected, args),
      checkF(view, expected, args),
      checkRef(view)
    ).flatten

  /** As [[problems]], with what `apply` gives for these arguments as the
    * baseline.
    */
  private[quality] def problemsAgainstApply(
      view: AnyRef,
      args: Seq[Any]
  ): List[String] =
    method(view, "apply", args.size) match {
      case None    => List(s"apply/${args.size} not found on ${name(view)}")
      case Some(m) =>
        val expected = m.invoke(view, forApply(m, args): _*)
        problems(view, expected.asInstanceOf[Html], args)
    }

  /** `render` and `f` take a repeated parameter as an array, and `apply` takes
    * it as a sequence.
    */
  private def forApply(apply: Method, args: Seq[Any]): Seq[AnyRef] =
    apply.getParameterTypes.toSeq.zip(args).map {
      case (parameter, array: Array[_]) if parameter == classOf[Seq[_]] =>
        ArraySeq.unsafeWrapArray(array)
      case (_, arg) => arg.asInstanceOf[AnyRef]
    }

  /** What a template's `render` takes, by the types it declares. */
  private[quality] def parametersOf(view: AnyRef): List[Class[_]] =
    view.getClass.getMethods
      .filterNot(_.isBridge)
      .find(_.getName == "render")
      .map(_.getParameterTypes.toList)
      .getOrElse(
        throw new NoSuchElementException(s"render not found on ${name(view)}")
      )

  private def checkRender(
      view: AnyRef,
      expected: Html,
      args: Seq[Any]
  ): Option[String] =
    method(view, "render", args.size) match {
      case None    => Some(s"render/${args.size} not found on ${name(view)}")
      case Some(m) =>
        attempt("render")(m.invoke(view, args.map(_.asInstanceOf[AnyRef]): _*))
          .flatMap(sameAs(expected, "render"))
    }

  private def checkF(
      view: AnyRef,
      expected: Html,
      args: Seq[Any]
  ): Option[String] =
    method(view, "f", 0) match {
      case None    => Some(s"f not found on ${name(view)}")
      case Some(m) =>
        attempt("f")(applyCurried(m.invoke(view), args))
          .flatMap(sameAs(expected, "f"))
    }

  private def checkRef(view: AnyRef): Option[String] =
    method(view, "ref", 0) match {
      case None    => Some(s"ref not found on ${name(view)}")
      case Some(m) =>
        val got = m.invoke(view)
        if (got eq view) None
        else Some(s"ref returned ${name(got)} rather than the template itself")
    }

  /** `f` is curried the same way the template's parameter lists are, so the
    * arguments are fed in a group at a time.
    */
  private def applyCurried(f: AnyRef, args: Seq[Any]): AnyRef =
    f match {
      case xxl: FunctionXXL => applyXXL(xxl, args)
      case _                =>
        functionArity(f) match {
          case None         => f
          case Some(wanted) =>
            val (now, later) = args.splitAt(wanted)
            if (now.size < wanted)
              throw new IllegalStateException(
                s"f wanted $wanted more arguments, ${now.size} left"
              )
            applyCurried(invokeFunction(f, now), later)
        }
    }

  /** A function of more than 22 parameters takes them as an array, so it does
    * not say how many it wants and fails only on reading past the end of too
    * few. It is given the fewest that work.
    */
  private def applyXXL(f: FunctionXXL, args: Seq[Any]): AnyRef = {
    var lastFailure: Throwable = null
    (23 to args.size).iterator
      .flatMap { n =>
        val now = args.take(n).map(_.asInstanceOf[Object]).toArray
        try Some((f(IArray.unsafeFromArray(now)), args.drop(n)))
        catch {
          case e: ArrayIndexOutOfBoundsException =>
            lastFailure = e
            None
        }
      }
      .nextOption()
      .map { case (next, later) => applyCurried(next, later) }
      .getOrElse(
        // A body failing the same way looks like this too, so its cause is kept.
        throw new IllegalStateException(
          s"f took none of the numbers of arguments up to ${args.size}",
          lastFailure
        )
      )
  }

  private def functionArity(o: AnyRef): Option[Int] =
    (0 to 22).find(n => functionClass(n).isInstance(o))

  private def functionClass(n: Int): Class[_] =
    Class.forName(s"scala.Function$n")

  private def invokeFunction(f: AnyRef, args: Seq[Any]): AnyRef = {
    val m = functionClass(args.size).getMethods.find(_.getName == "apply").get
    m.invoke(f, args.map(_.asInstanceOf[AnyRef]): _*)
  }

  /** A template inherits a static `apply`, from the case class behind Twirl's
    * `BaseScalaTemplate`, and that is not its own.
    */
  private def method(view: AnyRef, named: String, arity: Int) =
    view.getClass.getMethods.find(m =>
      m.getName == named && m.getParameterCount == arity &&
        !Modifier.isStatic(m.getModifiers)
    )

  private def attempt(what: String)(f: => AnyRef): Option[AnyRef] =
    try Some(f)
    catch {
      case NonFatal(e) =>
        throw new AssertionError(s"$what threw ${rootCause(e)}", e)
    }

  private def sameAs(expected: Html, what: String)(
      got: AnyRef
  ): Option[String] =
    if (got.toString == expected.toString) None
    else Some(s"$what produced different html from apply")

  private[quality] def rootCause(e: Throwable): String =
    Option(e.getCause)
      .map(rootCause)
      .getOrElse(s"${e.getClass.getSimpleName}: ${e.getMessage}")

  private def name(o: AnyRef): String =
    o.getClass.getSimpleName.stripSuffix("$")

}

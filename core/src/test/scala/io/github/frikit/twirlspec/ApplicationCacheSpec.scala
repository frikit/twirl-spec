package io.github.frikit.twirlspec

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import io.github.frikit.twirlspec.render.{ApplicationCache, SharedApplication}

/** The cache in isolation, on applications this spec owns. */
class ApplicationCacheSpec extends AnyWordSpec with Matchers {

  private def newApp(config: Map[String, Any]): Application =
    new GuiceApplicationBuilder()
      .configure(config ++ Map("metrics.enabled" -> false, "auditing.enabled" -> false))
      .build()

  "the cache" should {

    "build once per configuration and hand back the same instance" in {
      var builds = 0
      val cache  = new ApplicationCache({ config => builds += 1; newApp(config) })

      val a = cache(Map("a" -> 1))
      val b = cache(Map("a" -> 1))
      a                     must be theSameInstanceAs b
      builds              mustBe 1
      cache.instanceCount mustBe 1

      cache(Map("a" -> 2))
      builds              mustBe 2
      cache.instanceCount mustBe 2

      cache.reset()
    }

    "stop everything it holds without forgetting it" in {
      val cache = new ApplicationCache(newApp)
      cache(Map("b" -> 1))
      cache.instanceCount mustBe 1
      cache.stopAll()
      cache.instanceCount mustBe 1 // stopped, still cached
      cache.reset()
    }

    "tolerate stopping an application twice" in {
      // What the JVM shutdown hook does after a suite has already stopped one.
      val cache = new ApplicationCache(newApp)
      cache(Map("c" -> 1))
      cache.stopAll()
      noException must be thrownBy cache.stopAll()
      cache.reset()
    }

    "forget everything on reset" in {
      val cache = new ApplicationCache(newApp)
      cache(Map("d" -> 1))
      cache.reset()
      cache.instanceCount mustBe 0
    }
  }

  "an application whose stop hook fails" should {
    "not stop the cache clearing the rest" in {
      val buildWithFailingStopHook: Map[String, Any] => Application = { config =>
        val built = newApp(config)
        built.injector
          .instanceOf[play.api.inject.ApplicationLifecycle]
          .addStopHook(() => scala.concurrent.Future.failed(new RuntimeException("stop hook exploded")))
        built
      }
      val cache                                                     = new ApplicationCache(buildWithFailingStopHook)
      cache(Map("e" -> 1))
      noException must be thrownBy cache.stopAll()
      cache.reset()
      cache.instanceCount mustBe 0
    }
  }

  "the shutdown hook" should {
    "leave the cache able to rebuild rather than handing out a stopped application" in {
      SharedApplication(Map.empty)
      SharedApplication.instanceCount must be >= 1

      SharedApplication.shutdownHook.run()
      SharedApplication.instanceCount mustBe 0

      // The next caller gets a working application, not a stopped one.
      val rebuilt = SharedApplication(Map.empty)
      rebuilt.injector.instanceOf[play.api.i18n.MessagesApi].messages must not be empty
      SharedApplication.instanceCount                               mustBe 1
    }
  }

  "the shared instance" should {
    "apply the view-test defaults and reuse one application" in {
      val a = SharedApplication(Map.empty)
      val b = SharedApplication(Map.empty)
      a                                                       must be theSameInstanceAs b
      a.configuration.get[Seq[String]]("play.i18n.langs")     must contain("en")
      SharedApplication.instanceCount                         must be >= 1
      SharedApplication.viewTestDefaults("metrics.enabled") mustBe false
    }
  }

}

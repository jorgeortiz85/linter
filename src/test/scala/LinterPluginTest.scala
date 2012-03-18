/**
 *   Copyright 2012 Foursquare Labs, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.foursquare.lint

import org.junit.{Before, Test}
import org.specs.SpecsMatchers

class LinterPluginTest extends SpecsMatchers {
  var linterPlugin: LinterPlugin = null

  class InitializationException(message: String) extends Exception(message)

  class Compiler(pluginOptions: List[String]) {
    import java.io.{PrintWriter, StringWriter}
    import scala.io.Source
    import scala.tools.nsc.{Global, Settings}
    import scala.tools.nsc.interpreter.{IMain, Results}
    import scala.tools.nsc.reporters.Reporter

    private val settings = new Settings
    val loader = manifest[LinterPlugin].erasure.getClassLoader
    settings.classpath.value = Source.fromURL(loader.getResource("app.class.path")).mkString
    settings.bootclasspath.append(Source.fromURL(loader.getResource("boot.class.path")).mkString)
    settings.deprecation.value = true // enable detailed deprecation warnings
    settings.unchecked.value = true // enable detailed unchecked warnings
    settings.Xwarnfatal.value = false // but not this because we're testing the difference between error and warning

    val stringWriter = new StringWriter()

    // This is deprecated in 2.9.x, but we need to use it for compatibility with 2.8.x
    private val interpreter = new IMain(settings, new PrintWriter(stringWriter)) {
      override protected def newCompiler(settings: Settings, reporter: Reporter) = {
        settings.outputDirs setSingleOutput virtualDirectory
        new Global(settings, reporter) {
          override protected def computeInternalPhases () {
            super.computeInternalPhases
            linterPlugin = new LinterPlugin(this) {
              override def openPropertiesFile(filename: String) =
                Option(getClass.getClassLoader.getResourceAsStream(filename)).getOrElse {
                  throw new java.io.FileNotFoundException("No such file " + filename)
                }
            }

            // is this the right place?
            linterPlugin.processOptions(pluginOptions, msg => throw new InitializationException(msg))

            for (phase <- linterPlugin.components)
              phasesSet += phase
          }
        }
      }
    }

    def compileAndLint(code: String): Option[String] = {
      stringWriter.getBuffer.delete(0, stringWriter.getBuffer.length)
      val thunked = "() => { %s }".format(code)
      interpreter.interpret(thunked) match {
        case Results.Success if stringWriter.toString.indexOf("warning") == -1 => None
        case Results.Success | Results.Error => Some(stringWriter.toString)
        case Results.Incomplete => throw new Exception("Incomplete code snippet")
      }
    }
  }

  val compilerCache = new scala.collection.mutable.HashMap[List[String], Compiler]
  def compilerFor(options: List[String]): Compiler = {
    compilerCache.get(options) match {
      case Some(compiler) => compiler
      case None =>
        val compiler = new Compiler(options)
        compilerCache += options -> compiler
        compiler
    }
  }

  def check(code: String, expectedError: Option[String] = None, options: List[String] = Nil) {
    // Either they should both be None or the expected error should be a
    // substring of the actual error.

    (expectedError, compilerFor(options).compileAndLint(code)) must beLike {
      case (None, None) => true
      case (Some(exp), Some(act)) => act.contains(exp)
    }
  }

  @Before
  def forceCompilerInit(): Unit = {
    check("""1 + 1""", None)
  }

  @Test
  def nonExistantFileErrors(): Unit = {
    check("""1 + 1""", None, List("config:thisfiledoesnotexistimsure")) must throwA[InitializationException]
  }

  @Test
  def nonBadOptionFileErrors(): Unit = {
    check("""1 + 1""", None, List("notavalidconfigoption")) must throwA[InitializationException]
  }

  def multiTest(message: String, basename: String)(impltest: (Option[String], List[String]) => Unit): Unit = {
    impltest(Some("warning: " + message), Nil)
    impltest(None, List("config:testprops/" + basename + "off.properties"))
    impltest(Some("error: " + message), List("config:testprops/" + basename + "error.properties"))
    impltest(Some("warning: " + message), List("config:testprops/allbut" + basename + "off.properties"))
    impltest(Some("warning: " + message), List("config:testprops/allbut" + basename + "error.properties"))
  }

  @Test
  def testHasVersusContains(): Unit = {
    multiTest("SeqLike[Int].contains(java.lang.String) will probably return false.", "unsafecontains") { (msg, options) =>
      check("""val x = List(4); x.contains("foo")""", msg, options)

      // Set and Map have type-safe contains methods so we don't want to warn on
      // those.
      check("""val x = Set(4); x.contains(3)""", options = options)
      check("""val x = Map(4 -> 5); x.contains(3)""", options = options)
    }
  }

  @Test
  def testNoOptionGet(): Unit = {
    multiTest("Calling .get on Option will throw an exception if the Option is None.", "optionget") { (msg, options) =>
      check("""Option(10).get""", msg, options)
      check("""val x: Option[Int] = None ; x.get""", msg, options)
      check("""val x: Option[Int] = Some(3); x.get""", msg, options)
      check("""val x = None ; x.get""", msg, options)
      check("""val x = Some(3) ; x.get""", msg, options)

      check("""Map(1 -> "1", 2 -> "2").get(1)""", options = options)
    }
  }

  @Test
  def testJavaConversionsImport(): Unit = {
    multiTest("Conversions in scala.collection.JavaConversions._ are dangerous.", "javaconversions") { (msg, options) =>
      check("import scala.collection.JavaConversions._;", msg, options)
    }
  }

  @Test
  def testUnsafeEquals(): Unit = {
    multiTest("Comparing with ==", "unsafeequals") { (msg, options) =>
      // Should warn
      check("Nil == None", msg, options)
      check("""{
        val x: List[Int] = Nil
        val y: List[String] = Nil
        x == y
      }""", msg, options)

      // Should compile
      check(""" "foo" == "bar" """, options = options)
      check("""{
        val x: List[Int] = Nil
        val y: List[Int] = Nil
        x == y
      }""", options = options)
      check("""{
        val x: String = "foo"
        val y: String = "bar"
        x == y
      }""", options = options)
      check("""{
        val x: String = "foo"
        x == "bar"
      }""", options = options)
    }
  }
}

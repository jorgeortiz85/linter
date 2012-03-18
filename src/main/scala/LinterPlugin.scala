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

import scala.reflect.generic.Flags
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

import java.io.{InputStream, FileInputStream, IOException}

class LinterPlugin(val global: Global) extends Plugin {
  import global._

  val name = "linter"
  val description = ""
  val components = List[PluginComponent](LinterComponent)
  var warningEnabled = (_: Warnings.Warning) => true

  object Warnings extends Enumeration {
    type Warning = Value
    val JavaConversions,
        OptionGet,
        UnsafeContains,
        UnsafeEquals = Value
  }

  val OptionConfig = "config:"

  override def processOptions(options: List[String], error: String => Unit) {
    for(option <- options) {
      if(option.startsWith(OptionConfig)) loadConfiguration(option.drop(OptionConfig.length), error)
      else error("Unknown option: " + option)
    }
  }

  private def underscoreify(camelCase: String): String = // not super-smart, but sufficient unto the purpose
    camelCase.replaceAll("([A-Z])","_$1").toLowerCase.dropWhile(_ == '_')

  private def loadConfiguration(filename: String, error: String => Unit) {
    import java.util.Properties
    import scala.collection.JavaConverters._

    val props = new Properties

    try {
      val stream = openPropertiesFile(filename)
      try {
        props.load(stream)
      } finally {
        stream.close()
      }
    } catch {
      case e: IOException =>
        error("Exception occurred while loading properties file: " + e.getMessage)
        return
    }

    warningEnabled =
      Warnings.values.foldLeft(Map.empty[Warnings.Warning, Boolean]) { (warningConfig, warning) =>
        // Anything other than "false" is treated as enabling the warning
        warningConfig + (warning -> !(props.getProperty("check." + underscoreify(warning.toString), "true") == "false"))
      }
  }

  protected def openPropertiesFile(filename: String): InputStream =
    new FileInputStream(filename)

  private object LinterComponent extends PluginComponent {
    import global._

    val global = LinterPlugin.this.global

    override val runsAfter = List("typer")

    val phaseName = "linter"

    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      override def apply(unit: global.CompilationUnit): Unit = {
        new LinterTraverser(unit).traverse(unit.body)
      }
    }

    class LinterTraverser(unit: CompilationUnit) extends Traverser {
      import definitions.{AnyClass, ObjectClass, Object_==, OptionClass, SeqClass}

      val JavaConversionsModule: Symbol = definitions.getModule("scala.collection.JavaConversions")
      val SeqLikeClass: Symbol = definitions.getClass("scala.collection.SeqLike")
      val SeqLikeContains: Symbol = SeqLikeClass.info.member(newTermName("contains"))
      val OptionGet: Symbol = OptionClass.info.member(nme.get)

      def SeqMemberType(seenFrom: Type): Type = {
        SeqLikeClass.tpe.typeArgs.head.asSeenFrom(seenFrom, SeqLikeClass)
      }

      def isSubtype(x: Tree, y: Tree): Boolean = {
        x.tpe.widen <:< y.tpe.widen
      }

      def methodImplements(method: Symbol, target: Symbol): Boolean = {
        method == target || method.allOverriddenSymbols.contains(target)
      }

      def isGlobalImport(selector: ImportSelector): Boolean = {
        selector.name == nme.WILDCARD && selector.renamePos == -1
      }

      override def traverse(tree: Tree): Unit = tree match {
        case Apply(eqeq @ Select(lhs, nme.EQ), List(rhs))
            if warningEnabled(Warnings.UnsafeEquals) && methodImplements(eqeq.symbol, Object_==) && !(isSubtype(lhs, rhs) || isSubtype(rhs, lhs)) =>
          val warnMsg = "Comparing with == on instances of different types (%s, %s) will probably return false."
          unit.warning(eqeq.pos, warnMsg.format(lhs.tpe.widen, rhs.tpe.widen))

        case Import(pkg, selectors)
            if warningEnabled(Warnings.JavaConversions) && pkg.symbol == JavaConversionsModule && selectors.exists(isGlobalImport) =>
          unit.warning(pkg.pos, "Conversions in scala.collection.JavaConversions._ are dangerous.")

        case Apply(contains @ Select(seq, _), List(target))
            if warningEnabled(Warnings.UnsafeContains) && methodImplements(contains.symbol, SeqLikeContains) && !(target.tpe <:< SeqMemberType(seq.tpe)) =>
          val warnMsg = "SeqLike[%s].contains(%s) will probably return false."
          unit.warning(contains.pos, warnMsg.format(SeqMemberType(seq.tpe), target.tpe.widen))

        case get @ Select(_, nme.get)
            if warningEnabled(Warnings.OptionGet) && methodImplements(get.symbol, OptionGet) =>
          if (!get.pos.source.path.contains("src/test")) {
            unit.warning(get.pos, "Calling .get on Option will throw an exception if the Option is None.")
          }

        case _ =>
          super.traverse(tree)
      }
    }
  }
}

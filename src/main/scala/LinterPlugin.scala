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
  var warningActions = (_: Warnings.Warning) => Actions.Warn

  def warningEnabled(warning: Warnings.Warning) = warningActions(warning) != Actions.NoAction

  object Actions extends Enumeration {
    type Action = Value
    val NoAction,
        Warn,
        Error = Value
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

    val NoAction = underscoreify(Actions.NoAction.toString)
    val Warn = underscoreify(Actions.Warn.toString)
    val Error = underscoreify(Actions.Error.toString)

    val defaultAction = props.getProperty("default_action", Warn) match {
      case action@(NoAction|Warn|Error) => action
      case other =>
        error("Unknown action for default_action: " + other)
        return
    }

    warningActions =
      Warnings.values.foldLeft(Map.empty[Warnings.Warning, Actions.Action]) { (warningConfig, warning) =>
        val action = props.getProperty("check." + underscoreify(warning.toString), defaultAction) match {
          case NoAction => Actions.NoAction
          case Warn => Actions.Warn
          case Error => Actions.Error
          case other =>
            error("Unknown action for warning " + warning + ": " + other)
            return
        }
        warningConfig + (warning -> action)
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
      val actions = List(new UnsafeEquals(global),
                         new UnsafeContains(global),
                         new OptionGet(global),
                         new JavaConversions(global)).filter(a => warningEnabled(a.warning))

      override def traverse(tree: Tree): Unit = {
        // I hate thes .asInstanceOfs.  But I cannot convince the
        // compiler to thread the dependent types through!
        actions.find(a => a.action.isDefinedAt(tree.asInstanceOf[a.global.Tree])) match {
          case Some(action) =>
            val (pos, msg) = action.action(tree.asInstanceOf[action.global.Tree])
            if(warningActions(action.warning) == Actions.Error) unit.error(pos, msg)
            else unit.warning(pos, msg)
          case None =>
            super.traverse(tree)
        }
      }
    }
  }
}

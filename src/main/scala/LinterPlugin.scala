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

class LinterPlugin(val global: Global) extends Plugin {
  import global._

  val name = "linter"
  val description = ""
  val components = List[PluginComponent](LinterComponent)

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

      var moduleStack: List[Symbol] = Nil

      override def traverse(tree: Tree): Unit = tree match {
        case Apply(eqeq @ Select(lhs, nme.EQ), List(rhs))
            if methodImplements(eqeq.symbol, Object_==) && !(isSubtype(lhs, rhs) || isSubtype(rhs, lhs)) =>
          val warnMsg = "Comparing with == on instances of different types (%s, %s) will probably return false."
          unit.warning(eqeq.pos, warnMsg.format(lhs.tpe.widen, rhs.tpe.widen))

        case Import(pkg, selectors)
            if pkg.symbol == JavaConversionsModule && selectors.exists(isGlobalImport) =>
          unit.warning(pkg.pos, "Conversions in scala.collection.JavaConversions._ are dangerous.")

        case Apply(contains @ Select(seq, _), List(target))
            if methodImplements(contains.symbol, SeqLikeContains) && !(target.tpe <:< SeqMemberType(seq.tpe)) =>
          val warnMsg = "SeqLike[%s].contains(%s) will probably return false."
          unit.warning(contains.pos, warnMsg.format(SeqMemberType(seq.tpe), target.tpe.widen))

        case get @ Select(_, nme.get) if methodImplements(get.symbol, OptionGet) =>
          if (!get.pos.source.path.contains("src/test")) {
            unit.warning(get.pos, "Calling .get on Option will throw an exception if the Option is None.")
          }

        // keep track of the module definition we are inside of
        case m: ModuleDef =>
          val oldStack = moduleStack

          moduleStack ::= m.symbol
          m.children.foreach(super.traverse)
          moduleStack = oldStack

        case _: Ident =>
          warnCompanionClassUsage(tree)

        case sel: Select =>
          if (!warnCompanionClassUsage(tree))
            super.traverse(sel.qualifier)

        case _ =>
          super.traverse(tree)
      }

      def warnCompanionClassUsage(tree: Tree): Boolean = {
        val sym = tree.symbol

        val condition =
          sym.isModule &&
          sym.companionClass.isCaseClass &&

          // there's a spurious auto-generated reference inside of the
          // companion object itself in its `readResolve`, so we
          // don't report inside of the companion object itself
          !moduleStack.contains(sym)

        if (condition)
          unit.warning(tree.pos, Warnings.SuspiciousCompanionObjectUse)

        condition
      }

    }
  }

  object Warnings {
    val SuspiciousCompanionObjectUse = "Using the companion object of a case class here is probably not what you intended. You probably meant some instance of the case class instead."
  }
}

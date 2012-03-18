package com.foursquare.lint

import scala.tools.nsc.Global

abstract class LinterAction(val global: Global, val warning: Warnings.Warning) {
  import global._

  def action: PartialFunction[Tree, (Position, String)]

  // Utility predicates that are used by multiple warning types

  def methodImplements(method: Symbol, target: Symbol): Boolean = {
    method == target || method.allOverriddenSymbols.contains(target)
  }
}

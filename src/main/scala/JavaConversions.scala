package com.foursquare.lint

import scala.tools.nsc.Global

class JavaConversions(g: Global) extends LinterAction(g, Warnings.JavaConversions) {
  import global._

  val JavaConversionsModule: Symbol = definitions.getModule("scala.collection.JavaConversions")

  def isGlobalImport(selector: ImportSelector): Boolean = {
    selector.name == nme.WILDCARD && selector.renamePos == -1
  }

  val action: PartialFunction[Tree, (Position, String)] = {
    case Import(pkg, selectors)
        if pkg.symbol == JavaConversionsModule && selectors.exists(isGlobalImport) =>
      (pkg.pos, "Conversions in scala.collection.JavaConversions._ are dangerous.")
  }
}

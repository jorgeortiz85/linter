package com.foursquare.lint

import scala.tools.nsc.Global

class OptionGet(g: Global) extends LinterAction(g, Warnings.OptionGet) {
  import global._

  import definitions.OptionClass
  val OptionGet: Symbol = OptionClass.info.member(nme.get)

  val action: PartialFunction[Tree, (Position, String)] = {
    case get @ Select(_, nme.get)
        if methodImplements(get.symbol, OptionGet) && !get.pos.source.path.contains("src/test") =>
      (get.pos, "Calling .get on Option will throw an exception if the Option is None.")
  }
}

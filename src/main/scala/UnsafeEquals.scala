package com.foursquare.lint

import scala.tools.nsc.Global

class UnsafeEquals(g: Global) extends LinterAction(g, Warnings.UnsafeEquals) {
  import global._
  import definitions.Object_==

  def isSubtype(x: Tree, y: Tree): Boolean = {
    x.tpe.widen <:< y.tpe.widen
  }

  val action: PartialFunction[Tree, (Position, String)] = {
    case Apply(eqeq @ Select(lhs, nme.EQ), List(rhs))
        if methodImplements(eqeq.symbol, Object_==) && !(isSubtype(lhs, rhs) || isSubtype(rhs, lhs)) =>
      val warnMsg = "Comparing with == on instances of different types (%s, %s) will probably return false."
      (eqeq.pos, warnMsg.format(lhs.tpe.widen, rhs.tpe.widen))
  }
}


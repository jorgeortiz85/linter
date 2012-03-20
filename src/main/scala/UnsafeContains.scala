package com.foursquare.lint

import scala.tools.nsc.Global

class UnsafeContains(g: Global) extends LinterAction(g, Warnings.UnsafeContains) {
  import global._

  val SeqLikeClass: Symbol = definitions.getClass("scala.collection.SeqLike")
  val SeqLikeContains: Symbol = SeqLikeClass.info.member(newTermName("contains"))

  def SeqMemberType(seenFrom: Type): Type = {
    SeqLikeClass.tpe.typeArgs.head.asSeenFrom(seenFrom, SeqLikeClass)
  }

  val action: PartialFunction[Tree, (Position, String)] = {
    case Apply(contains @ Select(seq, _), List(target))
        if methodImplements(contains.symbol, SeqLikeContains) && !(target.tpe <:< SeqMemberType(seq.tpe)) =>
      val warnMsg = "SeqLike[%s].contains(%s) will probably return false."
      (contains.pos, warnMsg.format(SeqMemberType(seq.tpe), target.tpe.widen))
  }
}

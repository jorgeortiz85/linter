package com.foursquare.lint

import com.typesafe.config.ConfigFactory 

/**
 * <p>Configuration class for running (or not) different lint checks</p>
 * <p>As a library, linter defaults to including the src/main/resources/reference.conf settings
 * which are override-able by an including application.</p>
 */
object LinterConfig {

  val config = ConfigFactory.load("linter")
  config.checkValid(ConfigFactory.defaultReference(), "linter")
  
  val eqeqCheckEnabled = config.getBoolean("linter.eqeq.checkEnabled")
  val eqeqSeverity = LinterSeverity.withName(config.getString("linter.eqeq.severity"))
  
  val packageWildcardWhitelistCheckEnabled = config.getBoolean("linter.package.wildcard.whitelist.checkEnabled")
  val packageWildcardWhitelistPackages = config.getStringList("linter.package.wildcard.whitelist.packages")
  val packageWildcardWhitelistSeverity = LinterSeverity.withName(config.getString("linter.package.wildcard.whitelist.severity"))
  
  val seqContainsCheckEnabled = config.getBoolean("linter.seq.contains.checkEnabled")
  val seqContainsSeverity = LinterSeverity.withName(config.getString("linter.seq.contains.severity"))
  
  val optionGetCheckEnabled = config.getBoolean("linter.option.get.checkEnabled")
  val optionGetSeverity = LinterSeverity.withName(config.getString("linter.option.get.severity"))
}

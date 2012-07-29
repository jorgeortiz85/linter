# Linter Compiler Plugin

This is a compiler plugin that adds additional lint checks to protect against sharp corners
in the Scala compiler and standard libraries.

It's a work in progress.  For an overview of writing compiler plugins, see http://www.scala-lang.org/node/140

## Usage

Add it as a compiler plugin in your project by editing your build.sbt file.  For example, once published:

    addCompilerPlugin("com.foursquare.lint" %% "linter" % "x.y.z")

Or, until published:

    scalacOptions += "-Xplugin:..path-to-jar../linter.jar"

Optionally, run `sbt console` in this project to see it in action.

## Currently supported warnings

Note: for configuration documentation, unless otherwise stated, any "checkEnabled" configuration option
is one of {"true", "false"} and any severity configuration option is one of {"warn", "error"}.
Configuration documentation examples show the default settings that ship with this linter library.

### Unsafe `==`

    scala> Nil == None
    <console>:29: warning: Comparing with == on instances of different types (object Nil, object None) will probably return false.
                  Nil == None
                      ^

    Configuration:
        linter.eqeq.checkEnabled=true
        linter.eqeq.severity=error

### Unsafe `contains`

    scala> List(1, 2, 3).contains("4")
    <console>:29: warning: SeqLike[Int].contains(java.lang.String) will probably return false.
                  List(1, 2, 3).contains("4")
                                ^

    Configuration:
        linter.seq.contains.checkEnabled=true
        linter.seq.contains.severity=warn


### Wildcard import from `scala.collection.JavaConversions`

    scala> import scala.collection.JavaConversions._
    <console>:29: warning: Conversions in scala.collection.JavaConversions._ are dangerous.
           import scala.collection.JavaConversions._
                                   ^

    Configuration:
        None at present

### Any and all wildcard imports

    scala> import scala.collection.JavaConversions._
    <console>:10: warning: Wildcard imports should be avoided.
           import scala.reflect.generic._


    Configuration:
        linter.package.wildcard.whitelist.checkEnabled=true
        linter.package.wildcard.whitelist.severity=warn
        linter.package.wildcard.whitelist.packages=[]

### Calling `Option#get`

    scala> Option(1).get
    <console>:29: warning: Calling .get on Option will throw an exception if the Option is None.
                  Option(1).get
                            ^

    Configuration:
        linter.option.get.checkEnabled=true
        linter.option.get.severity=warn

## Configuring Linter

Linter is configured with the com.typesafe.config library (https://github.com/typesafehub/config)

In your application, you can override any of the configuration options from these locations (in priority-respecting order):
* system properties
* application.conf available on the classpath
* application.json available on the classpath
* application.properties available on the classpath

## Future Work

* Add more warnings
* Support "blacklist"ing imports and re-write the scala.collection.JavaCollections check with that mechanism

### Ideas for new warnings

Feel free to implement these, or add your own ideas. Pull requests welcome!

* Modularize the wildcard import warnings like the "Avoid Star Import" configuration of checkstyle
 (http://checkstyle.sourceforge.net/config_imports.html)
* Require explicit `override` whenever a method is being overwritten
* Implicit methods should always have explicit return types
* Expressions spanning multiple lines should be enclosed in parentheses
* Unused method argument warnings
* Warn on unrestricted catch clauses (`case e => ...`)
* Traversable#head, Traversable#last, Traversable#maxBy
* Warn on shadowing variables, especially those of the same type
* Warn on inexhaustive pattern matching

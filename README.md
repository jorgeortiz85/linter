# Linter Compiler Plugin

This is a compiler plugin that adds additional lint checks to protect against sharp corners
in the Scala compiler and standard libraries.

It's a work in progress.

## Usage

Add it as a compiler plugin in your project, or run `sbt console` in this project to see it in action.

The plugin can optionally use a properties file (provided with `-P
linter:config:/path/to/it` on the scalac command line) that allows
controlling the action taken by the plugin either globally or on a
per-warning basis. The default action is `warn` and may be changed to
`no_action` or `error` by setting the `default_action` value in the
properties file.  The default can be overridden on a per-check basis
with any of those three options.

## Currently suported warnings

### Unsafe `==`

The configuration property is `check.unsafe_equals`.

    scala> Nil == None
    <console>:29: warning: Comparing with == on instances of different types (object Nil, object None) will probably return false.
                  Nil == None
                      ^

### Unsafe `contains`

The configuration property is `check.unsafe_contains`.

    scala> List(1, 2, 3).contains("4")
    <console>:29: warning: SeqLike[Int].contains(java.lang.String) will probably return false.
                  List(1, 2, 3).contains("4")
                                ^


### Wildcard import from `scala.collection.JavaConversions`

The configuration property is `check.java_conversions`.

    scala> import scala.collection.JavaConversions._
    <console>:29: warning: Conversions in scala.collection.JavaConversions._ are dangerous.
           import scala.collection.JavaConversions._
                                   ^

### Calling `Option#get`

The configuration property is `check.option_get`.

    scala> Option(1).get
    <console>:29: warning: Calling .get on Option will throw an exception if the Option is None.
                  Option(1).get
                            ^

## Future Work

* Add more warnings

### Ideas for new warnings

Feel free to implement these, or add your own ideas. Pull requests welcome!

* Warn on wildcard imports (either all with whitelist, or blacklist)
* Require explicit `override` whenever a method is being overwritten
* Implicit methods should always have explicit return types
* Expressions spanning multiple lines should be enclosed in parentheses
* Unused method argument warnings
* Warn on unrestricted catch clauses (`case e => ...`)
* Traversable#head, Traversable#last, Traversable#maxBy
* Warn on shadowing variables, especially those of the same type
* Warn on inexhaustive pattern matching

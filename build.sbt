libraryDependencies <++= (scalaVersion) { (scalaVer) =>
  Seq(
    "org.scala-lang"           % "scala-compiler"  % scalaVer,
    "com.typesafe"             % "config"          % "0.5.0",
    "org.scala-tools.testing" %% "specs"           % "1.6.9"  % "test" withSources(),
    "junit"                    % "junit"           % "4.8.2"  % "test" withSources(),
    "com.novocode"             % "junit-interface" % "0.7"    % "test"
  )
}

scalacOptions in console in Compile <++= (packageBin in Compile, managedClasspath in Compile) map { (pluginJar, cp) =>
  val configJar = cp.map(_.data).find(_.toString.contains("config-0.5.0.jar"))
  Seq("-Xplugin:"+pluginJar) ++ configJar.map("-Xplugin:"+_)
}

lazy val root = (project in file("."))
  .settings(
    name         := "orety",
    organization := "polyvariant",
    version      := "1.0",
    scalaVersion := "3.6.2",
    scalacOptions ++= List("-unchecked", "-deprecation", "-explain"),
    libraryDependencies ++= Seq(
      "org.typelevel"       %% "cats-effect"         % "3.5.7"
    )
  )

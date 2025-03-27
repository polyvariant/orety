ThisBuild / tlBaseVersion := "0.0"
ThisBuild / organization := "org.polyvariant"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("miciek", "Michał Płachta"))
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / tlFatalWarnings := false
ThisBuild / tlFatalWarningsInCi := false

ThisBuild / scalaVersion := "3.6.2"
ThisBuild / crossScalaVersions := Seq("3.6.2")

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

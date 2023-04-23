ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "zio-rock-the-jvm"
  )

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.18",
  "dev.zio" %% "zio-streams" % "1.0.18"
)

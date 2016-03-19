lazy val baseName = "Inertia"
lazy val baseNameL = baseName.toLowerCase

name := baseName

lazy val commonSettings = Seq(
  version              := "0.32.1",
  organization         := "de.sciss",
  scalaVersion         := "2.11.8",
  autoScalaLibrary     := false,
  crossPaths           := false,
  javacOptions in Compile ++= Seq("-g", "-target", "1.6", "-source", "1.6"),
  javacOptions in (Compile, doc) := Nil,
  description          := "Historical Piece of Software for the Piece CCC",
  homepage             := Some(url(s"https://github.com/Sciss/${name.value}")),
  licenses             := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt")),
  libraryDependencies  += "de.sciss" % "jcollider" % "1.0.0",
  mainClass in Compile := Some("de.sciss.inertia.Main")
)

lazy val root: Project = Project(id = baseNameL, base = file("."))
  .settings(commonSettings)

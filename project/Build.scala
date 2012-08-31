import java.util.Properties
import sbt._
import Keys._

object ExperimentalBuild extends Build {

  lazy val project = Project("project", file("."),
    settings = buildSettings ++ compileSettings
  )

  // SETTINGS

  lazy val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ Seq(
    name := "sbt-experiment",
    version := "1.0",
    scalaVersion := "2.9.2"
  )

  lazy val compileSettings: Seq[Setting[_]] =
    Seq(
      renameTask in Compile <<= generateRenamedResourcesTask,
      propsTask in Compile <<= generatePropertiesTask
    )

  // TASK DEFINITIONS

  val propsTask = TaskKey[(Properties, Seq[File])]("props", "Generates a property file and renamed unmanaged resources.")

  val renameTask = TaskKey[Seq[(File, File)]]("rename", "Generates renamed unmanaged resources.")

  // TASK IMPLEMENTATIONS

  val generatePropertiesTask =
    (renameTask in Compile, resourceDirectory in Compile, resourceManaged in Compile, streams) map {
      (mappings, sourceDir, targetDir, streams) =>
        val log = streams.log

        def getRelativePath(file: File, base: File): String = {
          file.relativeTo(base) match {
            case Some(relativeFile) => '/' + relativeFile.getPath
            case None => "/"
          }
        }

        val properties = new Properties()
        mappings.foreach {
          case (source, target) => {
            log.info("source: " + source)
            log.info("target: " + target)
            log.info("targetDir: " + targetDir)
            val relativeSource = getRelativePath(source, sourceDir)
            val relativeTarget = getRelativePath(target, targetDir)
            properties.put(relativeSource, relativeTarget)
          }
        }
        val targets = mappings map {
          case (source, target) => target
        }
        (properties, targets)

    }

  val generateRenamedResourcesTask =
    (resourceDirectory in Compile, resourceManaged in Compile, resources in Compile, streams) map {
      (sourceDir, targetDir, resrcs, streams) =>
        val log = streams.log
        val mappings = (resrcs --- sourceDir) x (mapToHashedFilenames(sourceDir, targetDir))
        log.info("Resource mappings: " + mappings.mkString("\n\t", "\n\t", ""))
        mappings
    }

  // HELPERS

  private val digester = java.security.MessageDigest.getInstance("SHA-1")

  private def mapToHashedFilenames(oldBase: File, newBase: File): FileMap = {
    file => {
      val relativePath: String = {
        file.relativeTo(oldBase) match {
          case Some(relativeFile) => {
            val path = relativeFile.getPath
            if (file.isFile) {
              path.replaceFirst("\\.[^\\.]+$", "." + hash(file) + "$0")
            } else path
          }
          case None => ""
        }
      }
      Some(newBase / relativePath)
    }
  }

  private def hash(file: File): String = {
    val source = scala.io.Source.fromFile(file, "latin1")
    val bytes = source.map(_.toByte).toArray
    source.close()
    val byteData = digester.digest(bytes)
    byteData.foldLeft("") {
      (a, b) => a + (Integer.toString((b & 0xff) + 0x100, 16).substring(1))
    }.take(8)
  }

}

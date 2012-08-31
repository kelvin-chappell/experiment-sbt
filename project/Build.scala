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
//      resourceGenerators in Compile <+= (propsWriteTask in Compile).task,
        renameTask in Compile <<= generateRenamedResourcesTask,
      propsTask in Compile <<= generatePropertiesTask,
      propsWriteTask in Compile <<= generatePropertiesFileTask      ,
//      packageTask in Compile <<= generatePackageTask     ,
      packageTask in Compile <<= (packageBin in Compile).dependsOn(propsWriteTask in Compile),
      //      packageBin in Compile <<= (packageBin in Compile).dependsOn(propsWriteTask in Compile)
        includeFilter in Compile in managedResources := "*"
    )

  // TASK DEFINITIONS

  val renameTask = TaskKey[Seq[(File, File)]]("rename", "Generates renamed unmanaged resources.")

  val propsTask = TaskKey[(Properties, Seq[File])]("props", "Generates a Properties instance and renamed unmanaged resources.")

  val propsWriteTask = TaskKey[Seq[File]]("propw", "Writes a Properties instance to file.")

  val packageTask = TaskKey[File]("pack", "Builds a package including properties.")

  // TASK IMPLEMENTATIONS

  val generateRenamedResourcesTask =
    (resourceDirectory in Compile, resourceManaged in Compile, resources in Compile, cacheDirectory, streams) map {
      (sourceDir, targetDir, resrcs, cache, streams) =>
        val log = streams.log
        val mappings = (resrcs --- sourceDir) x (mapToHashedFilenames(sourceDir, targetDir))
        log.info("Resource mappings: " + mappings.mkString("\n\t", "\n\t", ""))

        // sync file system
        val cacheFile = cache / "test"
        Sync(cacheFile)(mappings)

        mappings
    }

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
            //            val relativeTarget = (target --- targetDir).toString
            log.info("rel target: " + relativeTarget)
            properties.put(relativeSource, relativeTarget)
          }
        }
        val targets = mappings map {
          case (source, target) => target
        }
        (properties, targets)

    }

  val generatePropertiesFileTask =
    (propsTask in Compile, resourceManaged in Compile, streams) map {
        (propsAndMappings, targetDir, streams) =>
        val log = streams.log
        val props = propsAndMappings._1
        val comment = "testing123"
        val file = targetDir / "test.properties"
        IO.write(props, comment, file)
        Seq(file)
    }

  val generatePackageTask =
    (propsWriteTask in Compile, packageBin in Compile, streams) map {
      (propsFile, target, streams) =>
        val log = streams.log
    }

  // HASHING

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

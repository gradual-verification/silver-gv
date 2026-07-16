import java.nio.file.Paths

import TestHelpers.MockSilFrontend
import org.scalatest.funsuite.AnyFunSuite
import viper.silver.ast._
import java.io.File

class GradualTests extends AnyFunSuite {
  test("Parse gradual files") {
    def listFiles(folder: File, path: String): Seq[String] = {
      val content = folder.listFiles      
      val files = content.toList.filter(_.isFile)
                    .map(file => path + "/" + file.getName).toSeq
      val dirFiles = content.toList.filter(_.isDirectory)
                      .map(file => listFiles(file, path + "/" + file.getName))
                        .fold(Seq())((acc, l) => acc ++ l)

      files ++ dirFiles
    }

    val filePrefix = "gradual"
    val frontend = new MockSilFrontend

    val path = getClass.getResource(filePrefix)
    val folder = new File(path.getPath)
    val files = listFiles(folder, filePrefix) 

    files.foreach(testFile => {
      val fileRes = getClass.getResource(testFile)
      val file = Paths.get(fileRes.toURI)

      frontend.translate(file) match {
        case (None, errors) => sys.error("Error occurred during translating: " + errors)
        case _ => ()
      }
    })
  }
}

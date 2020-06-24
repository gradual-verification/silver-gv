import java.nio.file.Paths
import TestHelpers.MockSilFrontend
import org.scalatest.FunSuite
import viper.silver.ast._
import java.io.File
import scala.io.Source

// Class that tests every step up to and including Translation

class MyTests extends FunSuite {

  // List of folders to test, all must be paths back to resources ("transformations" or "all/basic" for example)
  val foldersToTest = Seq("all/basic")

  // Main method
  private def testAFolder(loc: String) {
    val path = getClass.getResource(loc)
    val folder = new File(path.getPath)
    recursiveListFiles(folder, loc)
  }

  // starts a test for a file
  // requites the file's name and it's path back to resources
  private def testAFile(loc: String, file: String) {
    val frontend = new MockSilFrontend

    val fullLoc = loc + "/" + file

    val fileStream = getClass.getResourceAsStream(fullLoc)
    assert(fileStream != null, s"File $fullLoc not found")
    val lines = Source.fromInputStream(fileStream).getLines

    var ignore = false

    lines.foreach(line =>
      if (line.startsWith("//:: IgnoreFile(/silicon"))
        ignore = true
    )

    if (ignore) {
      println("ignoring " + fullLoc)
    } else {
      test("testing " + fullLoc) {
        parse(fullLoc, frontend)
      }
    }
  }

  // recursively decends folders and tests all files in them
  // builds a path back to resources as it does so
  private def recursiveListFiles(f: File, path: String) {
    val these = f.listFiles
    assert(these != null, s"Folder $f not found")

    // testing of each file
    these.toList.filter(_.isFile)
      .foreach(file => testAFile(path, file.getName))

    // recursive calls to each subfolder
    these.toList.filter(_.isDirectory)
      .foreach(folder => recursiveListFiles(folder, path + "/" + folder.getName))
  }


  private def parse(testFile: String, frontend: MockSilFrontend): Unit = {

    val fileRes = getClass.getResource(testFile)
    assert(fileRes != null, s"File $testFile not found")
    val file = Paths.get(fileRes.toURI)
    var targetNode: Node = null

    //translate is in TestHelpers.scala and does every stage up to and including Translation on a file
    frontend.translate(file) match {
      case (Some(p), _) => targetNode = p
      case (None, errors) => sys.error("Error occurred during translating: " + errors)
    }
    assert (targetNode != null)
  }

  foldersToTest.foreach(folder => testAFolder(folder))
  //testAFolder(foldersToTest)
}
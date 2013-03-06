package semper.sil.frontend

import semper.sil.ast._
import semper.sil.parser.{PNode, Translator, Resolver, Parser}
import org.kiama.util.Messaging._
import semper.sil.verifier._
import java.io.File
import semper.sil.utility.Paths

/**
 * Common functionality to implement a command-line verifier for SIL.  This trait
 * provides code to invoke the parser, parse common command-line options and print
 * error messages in a user-friendly fashion.
 *
 * Users of this trait should implement a main method that calls `SilFrontend.run`.
 * Furthermore, they must implement the method `verifier` that returns a verifier
 * for SIL.
 *
 * @author Stefan Heule
 */
trait SilFrontend extends DefaultFrontend {

  /** The SIL verifier to be used for verification. */
  def verifier: Verifier

  override protected type ParserResult = PNode
  override protected type TypecheckerResult = Program

  /** The current configuration. */
  var _config: SilFrontendConfig = null
  def config = _config

  /**
   * Main method that parses command-line arguments, parses the input file and passes
   * the SIL program to the verifier.  The resulting error messages (if any) will be
   * shown in a user-friendly fashion.
   */
  def execute(args: Seq[String]) {

    val start = System.currentTimeMillis()

    // parse command line arguments
    var opts = List("--no-timing", "C:\\tmp\\sil\\cfg.dot")
    //opts = List("--version")
    try {
      _config = SilFrontendConfig(opts, verifier)
      config.file() // hack: force command-line option parsing
    } catch {
      case t: Exception =>
        printFinishHeaderWithTime(start)
        printErrors(CliOptionError(t.getMessage + "."))
        return
      case t: Throwable =>
        printFinishHeaderWithTime(start)
        printErrors(CliOptionError(t.toString + "."))
        return
    }

    // exit if there were errors during parsing of command-line options
    if (config.error.isDefined) {
      printHeader()
      printFinishHeader(start)
      printErrors(CliOptionError(config.error.get + "."))
      return
    } else if (config.exit) {
      return
    }

    // forward verifier arguments
    verifier.commandLineArgs(Nil)

    // wait with setting the version, such that the verifier can use command-line arguments first to determine
    // the versions of dependencies
    config.version(config.fullVersion)

    // print the header
    printHeader()

    // initialize the translator
    init(verifier)

    // set the file we want to verify
    reset(new File(config.file()))

    // run the parser, typechecker, and verifier
    verify()

    // print the result
    printFinishHeader(start)
    result match {
      case Success =>
        printSuccess()
      case Failure(errors) =>
        printErrors(errors: _*)
    }
  }

  def printHeader() {
    if (!config.noHeader()) {
      println(config.fullVersion)
      println()
    }
  }

  def printFinishHeader(startTime: Long) {
    if (config.noTiming()) {
      println(s"${verifier.name} finished.")
    } else {
      printFinishHeaderWithTime(startTime)
    }
  }

  def printFinishHeaderWithTime(startTime: Long) {
    val timeMs = System.currentTimeMillis() - startTime
    val time = f"${(timeMs / 1000.0)}%.3f seconds"
    println(s"${verifier.name} finished in $time.")
  }
  def printErrors(errors: AbstractError*) {
    println("The following errors were found:")
    for (e <- errors) {
      println("  " + e.readableMessage)
    }
  }

  def printSuccess() {
    println("No errors found.")
  }

  override def doParse(input: String): Result[ParserResult] = {
    val p = Parser.parse(input)
    p match {
      case Parser.Success(e, _) =>
        Succ(e)
      case Parser.Failure(msg, next) =>
        Fail(List(ParseError(s"Failure: $msg", SourcePosition(next.pos.line, next.pos.column))))
      case Parser.Error(msg, next) =>
        Fail(List(ParseError(s"Error: $msg", SourcePosition(next.pos.line, next.pos.column))))
    }
  }

  override def doTypecheck(input: ParserResult): Result[TypecheckerResult] = {
    Resolver.check(input)
    if (messagecount == 0) {
      val n = Translator.translate(input).asInstanceOf[Program]
      Succ(n)
    } else {
      val errors = for (m <- sortedmessages) yield {
        TypecheckerError(m.message, SourcePosition(m.pos.line, m.pos.column))
      }
      Fail(errors)
    }
  }

  override def doTranslate(input: TypecheckerResult): Result[Program] = {
    // no translation needed
    Succ(input)
  }

  override def mapVerificationResult(in: VerificationResult) = in

}
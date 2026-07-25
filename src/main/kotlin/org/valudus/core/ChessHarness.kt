package org.valudus.core

import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.TimeSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MaterialBalance(val whiteCentipawns: Int, val blackCentipawns: Int) {
    val whiteMinusBlack get() = whiteCentipawns - blackCentipawns
}

fun materialBalance(fen: String): MaterialBalance {
    val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900, 'a' to 720, 'c' to 870, 'k' to 0)
    val board = fen.substringBefore(' ').substringBefore('[')
    var white = 0; var black = 0
    board.forEach { symbol ->
        if (symbol.isDigit() || symbol == '/' || symbol == '~') return@forEach
        val value = values[symbol.lowercaseChar()] ?: error("unsupported Capablanca board symbol: $symbol")
        if (symbol.isUpperCase()) white += value else black += value
    }
    return MaterialBalance(white, black)
}

data class UciInfo(val scoreKind: String, val scoreValue: Int, val depth: Int?, val nodes: Int?, val nps: Int?, val pv: String?)
data class UciAnalysis(val bestmove: String, val info: UciInfo?, val elapsedSeconds: Double)

fun parseUciInfo(line: String): UciInfo? {
    if (!line.startsWith("info ")) return null
    val score = Regex("\\bscore\\s+(cp|mate)\\s+(-?\\d+)").find(line) ?: return null
    fun field(name: String) = Regex("\\b$name\\s+(\\d+)").find(line)?.groupValues?.get(1)?.toInt()
    return UciInfo(score.groupValues[1], score.groupValues[2].toInt(), field("depth"), field("nodes"), field("nps"), Regex("\\bpv\\s+(.+)$").find(line)?.groupValues?.get(1))
}

/** Bounded UCI client for a trusted local executable. It is not a sandbox. */
class UciEngine(private val executable: Path, private val timeoutSeconds: Double = 10.0) : AutoCloseable {
    private val lines = LinkedBlockingQueue<String>()
    private lateinit var process: Process
    private lateinit var writer: java.io.BufferedWriter

    fun start(): UciEngine {
        require(executable.toFile().isFile) { "engine executable does not exist: $executable" }
        process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        writer = process.outputStream.bufferedWriter()
        thread(isDaemon = true) { process.inputStream.bufferedReader().useLines { sequence -> sequence.forEach { lines.put(it) } } }
        send("uci"); until { it == "uciok" }; ready()
        return this
    }

    fun analyse(fen: String, variant: String, depth: Int): UciAnalysis {
        require(fen.isNotBlank()) { "FEN must not be empty" }; require(depth > 0) { "depth must be at least 1" }
        send("setoption name UCI_Variant value $variant"); send("ucinewgame"); ready(); send("position fen $fen")
        val mark = TimeSource.Monotonic.markNow(); send("go depth $depth")
        var latest: UciInfo? = null
        while (true) {
            val line = next(); parseUciInfo(line)?.let { latest = it }
            if (line.startsWith("bestmove ")) return UciAnalysis(line.split(Regex("\\s+"))[1], latest, mark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0)
        }
    }

    override fun close() { if (::process.isInitialized) { runCatching { send("quit") }; process.waitFor(1, TimeUnit.SECONDS); if (process.isAlive) process.destroyForcibly() } }
    private fun ready() { send("isready"); until { it == "readyok" } }
    private fun send(command: String) { writer.write(command); writer.newLine(); writer.flush() }
    private fun until(predicate: (String) -> Boolean) { while (!predicate(next())) Unit }
    private fun next(): String = lines.poll((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS) ?: error("engine did not answer within ${"%.1f".format(timeoutSeconds)}s")
}

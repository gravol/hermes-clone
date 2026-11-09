package com.gravol.hermesclone.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tombstone extraction. A native-crash trace is a debuggerd protobuf, and the only reason to read
 * it at all is to pull out the abort message and the faulting library — so the extraction is the
 * whole value of the native-crash path, and the first version of it threw on essentially every
 * real tombstone (flushing a run that started at -1). These tests pin the shape of that failure
 * against a synthetic blob rather than hoping a native crash shows up to exercise it.
 *
 * Control bytes below are written as Kotlin escapes, not literal characters: a source file
 * containing a real NUL byte is treated as binary by git, grep and most review tooling.
 */
class ProcessExitReportPrintableRunsTest {

    /**
     * Interleave printable text with control bytes to imitate a protobuf's length prefixes and
     * field tags around the strings it carries.
     */
    private fun blob(vararg parts: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        parts.forEach { out.write(it.toByteArray(Charsets.ISO_8859_1)) }
        return out.toByteArray()
    }

    /** A protobuf-ish blob: tags and length prefixes between the strings a tombstone actually carries. */
    private fun syntheticTombstone(): ByteArray = blob(
        "\u0002\u0012",
        "libhwui.so",
        " \u0000\u0001",
        "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0",
        ")\u0014",
        "pid: 4217, tid: 4231, name: RenderThread",
        "\u0000\u0000",
        "sk_surface_new_raster",
        "\u0001",
    )

    @Test
    fun `recovers the abort message and thread name from binary noise`() {
        val out = ProcessExitReport.printableRuns(syntheticTombstone())
        assertTrue("abort message missing: $out", out.contains("Fatal signal 11 (SIGSEGV)"))
        assertTrue("thread name missing: $out", out.contains("name: RenderThread"))
        assertTrue("faulting library missing: $out", out.contains("libhwui.so"))
        assertTrue("symbol missing: $out", out.contains("sk_surface_new_raster"))
    }

    @Test
    fun `runs shorter than the minimum are dropped`() {
        // "ab" and "cd" are 2-byte runs between non-printable bytes; only the long run survives.
        val bytes = byteArrayOf(0x00) + "ab".toByteArray() + byteArrayOf(0x00) +
            "cd".toByteArray() + byteArrayOf(0x00) + "abcdefgh".toByteArray() + byteArrayOf(0x00)
        val out = ProcessExitReport.printableRuns(bytes, minRun = 8)
        assertEquals("abcdefgh\n", out)
    }

    /**
     * The regression that made the first version useless: ending on a non-printable byte leaves no
     * open run, and the old code flushed `String(bytes, -1, size + 1)` — an exception, swallowed by
     * the caller's try/catch, which surfaced as "no trace available" for every real tombstone.
     */
    @Test
    fun `a blob ending on a non-printable byte neither throws nor loses the run`() {
        val bytes = byteArrayOf(0x00) + "Fatal signal 6 (SIGABRT)".toByteArray() +
            byteArrayOf(0x00, 0x00, 0x1f)
        val out = ProcessExitReport.printableRuns(bytes)
        assertEquals("Fatal signal 6 (SIGABRT)\n", out)
    }

    @Test
    fun `output stays under the character cap even with a long printable tail`() {
        val manyRuns = (1..400).joinToString("") { "run_${it}_of_the_tombstone_stream\n" }
        val tail = "Z".repeat(200_000)   // one enormous run beginning after the cap is reached
        val bytes = (manyRuns + tail).toByteArray(Charsets.ISO_8859_1)
        val out = ProcessExitReport.printableRuns(bytes, maxChars = 2000)
        assertTrue("cap defeated: ${out.length} chars", out.length < 2600)
    }

    @Test
    fun `no printable content yields an empty result rather than noise`() {
        assertEquals("", ProcessExitReport.printableRuns(ByteArray(512) { (it and 0x8F).toByte() }))
        assertEquals("", ProcessExitReport.printableRuns(ByteArray(0)))
    }

    @Test
    fun `high bytes are not treated as printable text`() {
        // 0xC3 0xA9 is UTF-8 for an accented letter — visible in a terminal, but not ASCII text
        // inside a protobuf, and must not be scraped up as a "string".
        val out = ProcessExitReport.printableRuns(byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0x41, 0x42))
        assertFalse("high bytes leaked into output: $out", out.contains("é"))
    }
}

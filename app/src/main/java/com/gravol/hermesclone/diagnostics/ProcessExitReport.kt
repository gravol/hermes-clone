package com.gravol.hermesclone.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import com.gravol.hermesclone.core.network.DebugLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-exit forensics: at startup, ask Android how the PREVIOUS process died and write the
 * answer into the debug journal.
 *
 * Why this exists. The journal only explains deaths that reach a Java handler: [HermexApplication]
 * installs an uncaught-exception handler that logs `ERROR|CRASH` and flushes synchronously, so an
 * ordinary crash is recorded. Every other death — native crash (SIGSEGV/SIGABRT in RenderThread or
 * Skia), ANR-then-kill, low-memory kill, `am kill`, force-stop — never runs that handler, so the
 * journal simply stops. Field reports of "crashing, no crash report" are exactly that signature:
 * the tail ends mid-stream with no CRASH line and no `AppLifecycle background` boundary, which
 * means the process was killed rather than crashed, and nothing in the log says by whom.
 *
 * `ActivityManager.getHistoricalProcessExitReasons()` is the system's own record of those deaths:
 * no permission is needed to read your own package's records (API 30; minSdk here is 34, so no
 * version guards), and it survives the death it describes. It gives reason and status, the
 * importance and memory (pss/rss) at the moment of death, a human description, and a trace stream:
 * text for ANR, and from API 31 a debuggerd tombstone protobuf for native crashes.
 *
 * Records are a system ring buffer and the trace lives in a SEPARATE global ring buffer that other
 * apps' crashes can overwrite, so traces may be missing — absence of a trace is not evidence that
 * there wasn't one.
 *
 * Levels: abnormal deaths log at ERROR, benign ones (user force-stop, package update, clean self
 * exit) at INFO, because the export filter carries ERROR/INFO but not custom levels.
 */
object ProcessExitReport {

    private const val MAX_RECORDS = 8
    /** ANR traces run to hundreds of KB; the interesting part (the main thread's stack, the
     *  reason line, memory) is at the head. */
    private const val MAX_TEXT_TRACE_CHARS = 6000
    /** Tombstone protobufs are binary; only printable runs of this length are kept. */
    private const val MIN_PRINTABLE_RUN = 8
    private const val MAX_NATIVE_TRACE_CHARS = 4000

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Read the previous exits and journal them. Off the main thread on purpose: the trace stream
     * is a file read, and `Application.onCreate` is exactly where this app has already ANRed once
     * over unbounded disk work (see DebugLog.readTail).
     */
    fun reportPreviousExits(context: Context) {
        Thread {
            try {
                readAndLog(context)
            } catch (t: Throwable) {
                // Forensics must never take the app down or block startup.
                DebugLog.log("ERROR", "ExitInfo", "read failed: ${t::class.simpleName}: ${t.message}")
            }
            DebugLog.flushNow()
        }.apply { name = "ExitInfoReport"; isDaemon = true; priority = Thread.NORM_PRIORITY; start() }
    }

    private fun readAndLog(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am == null) { DebugLog.log("INFO", "ExitInfo", "ActivityManager unavailable"); return }
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
        if (exits.isEmpty()) {
            DebugLog.log("INFO", "ExitInfo", "no process-exit records for this package")
            return
        }
        val newest = exits.first()
        DebugLog.log(
            if (isAbnormal(newest.reason)) "ERROR" else "INFO", "ExitInfo",
            "previous death: ${describe(newest)} — ${exits.size} records available (newest first)"
        )
        // The remaining records are older deaths. They are still worth one line each: the newest
        // one is often the benign force-stop that followed the crash the user actually cares about.
        exits.drop(1).forEach { r ->
            DebugLog.log(if (isAbnormal(r.reason)) "ERROR" else "INFO", "ExitInfo", "older death: ${describe(r)}")
        }
        // Only the newest death gets a trace dump — it is the one being investigated, and the
        // system may already have overwritten the older ones.
        if (isAbnormal(newest.reason)) dumpTrace(newest)
    }

    private fun describe(r: ApplicationExitInfo): String = buildString {
        append(stamp.format(Date(r.timestamp)))
        append(" reason=").append(reasonName(r.reason))
        append("(code=").append(r.reason).append(')')
        append(" status=").append(r.status)
        append(" importance=").append(importanceName(r.importance))
        append(" pss=").append(Math.round(r.pss / 1024.0)).append("MB")
        append(" rss=").append(Math.round(r.rss / 1024.0)).append("MB")
        r.description?.let { append(" desc=").append(it.replace('\n', ' ').take(240)) }
    }

    private fun dumpTrace(r: ApplicationExitInfo) {
        val text = try {
            if (r.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) extractPrintable(r)
            else readTextTrace(r)
        } catch (t: Throwable) {
            null
        }
        if (text.isNullOrBlank()) {
            DebugLog.log("INFO", "ExitInfo", "no trace available for the newest death (overwritten or not captured)")
            return
        }
        DebugLog.log("ERROR", "ExitInfo", "trace of ${reasonName(r.reason)} death:\n$text")
    }

    private fun readTextTrace(r: ApplicationExitInfo): String? {
        val stream = r.traceInputStream ?: return null
        val sb = StringBuilder()
        stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    sb.appendLine(line)
                    if (sb.length >= MAX_TEXT_TRACE_CHARS) {
                        sb.appendLine("... [trace truncated at ${sb.length} chars of a longer stream]")
                        return@useLines
                    }
                }
            }
        }
        return sb.toString().ifBlank { null }
    }

    /**
     * Native-crash traces arrive as a debuggerd tombstone protobuf, not text. Rather than write
     * binary into a text journal, keep the printable runs: that recovers the abort message, the
     * faulting library and symbol names, and the thread labels — enough to say which subsystem
     * died without a protobuf parser.
     */
    private fun extractPrintable(r: ApplicationExitInfo): String? {
        val stream = r.traceInputStream ?: return null
        val bytes = stream.use { it.readBytes() }
        val runs = printableRuns(bytes)
        if (runs.isBlank()) return null
        return runs + "\n[tombstone protobuf: ${bytes.size} bytes, printable runs only]"
    }

    /**
     * Extract printable runs from a binary blob, oldest/first bytes first.
     *
     * Two bugs this shape avoids, both found by reading the first version back rather than
     * trusting it: flushing the final run as `String(bytes, runStart, size - runStart)` throws
     * `StringIndexOutOfBoundsException` whenever the byte at the stop position is non-printable
     * (runStart == -1) — which is most tombstone bytes, so the whole extraction would have
     * silently returned "no trace available"; and breaking out of the loop on a size cap and then
     * flushing to `bytes.size` writes the entire remaining tail, defeating the cap. Accumulating
     * the current run in a buffer instead of tracking indices makes both impossible.
     */
    internal fun printableRuns(
        bytes: ByteArray,
        minRun: Int = MIN_PRINTABLE_RUN,
        maxChars: Int = MAX_NATIVE_TRACE_CHARS,
    ): String {
        if (bytes.isEmpty() || minRun <= 0) return ""
        val out = StringBuilder()
        val run = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E) {
                run.append(v.toChar())
            } else {
                if (run.length >= minRun) out.append(run).append('\n')
                run.setLength(0)
                if (out.length >= maxChars) break
            }
        }
        if (run.length >= minRun && out.length < maxChars) out.append(run).append('\n')
        return out.toString()
    }

    private fun isAbnormal(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        -> false
        else -> true
    }

    private fun reasonName(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF(clean self exit)"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED(killed by signal)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY(low memory killer)"
        ApplicationExitInfo.REASON_CRASH -> "CRASH(java exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE(native crash)"
        ApplicationExitInfo.REASON_ANR -> "ANR(not responding, killed)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$code"
    }

    /**
     * Note on the fields NOT read from ApplicationExitInfo: `getSubreason()`,
     * `getProcessDuration()` and `getProcessState()` are not in the SDK stubs at compileSdk 35
     * (the system still prints them in `dumpsys activity exit-info`, so the data exists — it is
     * just not reachable from an app on this SDK). Their absence costs little: the reason code,
     * importance, memory and the system's own [ApplicationExitInfo.getDescription] already say
     * what killed the process (e.g. "[INVALID START] killedByAm=true;...").
     */
    private fun importanceName(code: Int): String = when (code) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
        else -> "IMPORTANCE_$code"
    }
}

package com.gravol.hermesclone.feature.hub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the extractor to the official app's own Artifacts screen, captured
 * 2026-11-12 from session "Continue nous clone app handoff" (uiautomator
 * ground truth). If a rule changes, this test tells you WHICH parity broke.
 */
class ArtifactExtractionTest {

    private fun msg(o: String): JsonObject = Json.parseToJsonElement(o) as JsonObject

    private val session = listOf(
        // assistant text mentioning a spillover path
        msg("""{"role":"assistant","timestamp":100.0,"content":"see /home/jeff/.hermes/cache/spillover/call_850.txt for detail"}"""),
        // tool result: JSON content, output key text-scanned; paths separated by REAL newlines after parse
        msg("""{"role":"tool","timestamp":200.0,"content":"{\"output\": \"/home/jeff/HermesMobileClone/work/chat-parity-2026-11-12/HANDOFF.md\\n/home/jeff/HermesMobileClone/clone-app/PROJECT_HANDOFF.md\\nrestic-pass\\n/home/jeff/.hermes/google_token.json\", \"exit_code\": 0}"}"""),
        // later message re-mentions HANDOFF.md → its clock moves to 300
        msg("""{"role":"assistant","timestamp":300.0,"content":"updated HANDOFF at /home/jeff/HermesMobileClone/work/chat-parity-2026-11-12/HANDOFF.md"}"""),
        // tool_calls args alone are NOT extracted (verified against the official oracle
        // 2026-11-13: searching "proto.py" -> "No matching artifacts"). The row surfaces via
        // the write_file RESULT, whose output text names the path — official semantics.
        // NOTE: .py is not an official extension; the row must carry an ART_EXT extension.
        msg("""{"role":"assistant","timestamp":400.0,"tool_calls":[{"id":"t1","function":{"name":"write_file","arguments":"{\"path\": \"/home/jeff/.hermes/cache/scratch/art/proto_result.json\", \"content\": \"x\"}"}}]}"""),
        msg("""{"role":"tool","timestamp":450.0,"content":"{\"output\": \"Successfully wrote 512 bytes to /home/jeff/.hermes/cache/scratch/art/proto_result.json\", \"exit_code\": 0}"}"""),
        // MEDIA line (strict) + markdown link + pip junk + bare name
        msg("""{"role":"tool","timestamp":500.0,"content":"MEDIA: /sdcard/chart.png\n[report](https://example.com/files/report.md)\n/home/jeff/.cache/pip/wheels/thing-1.0-py3.whl\njust the words restic-pass here"}"""),
    )

    @Test fun officialArtifactSet() {
        ArtifactIndexer.extract(session).forEach { println("EXTRACT ${it.first} ${it.second} ${it.third}") }
        val got = ArtifactIndexer.extract(session).associate { it.first to it.second }
        assertEquals(ArtifactKind.Documents, got["/home/jeff/.hermes/cache/spillover/call_850.txt"])
        assertEquals(ArtifactKind.Documents, got["/home/jeff/HermesMobileClone/work/chat-parity-2026-11-12/HANDOFF.md"])
        assertEquals(ArtifactKind.Documents, got["/home/jeff/HermesMobileClone/clone-app/PROJECT_HANDOFF.md"])
        assertEquals(ArtifactKind.Data, got["/home/jeff/.hermes/google_token.json"])
        assertEquals(ArtifactKind.Data, got["/home/jeff/.hermes/cache/scratch/art/proto_result.json"])
        assertEquals(ArtifactKind.Images, got["/sdcard/chart.png"])
        assertEquals(ArtifactKind.Documents, got["https://example.com/files/report.md"])
        // official rejects: bare ls names, pip cache wheels
        assertNull(got["restic-pass"])
        assertNull(got["/home/jeff/.cache/pip/wheels/thing-1.0-py3.whl"])
        assertEquals(7, got.size)
    }

    @Test fun lastOccurrenceClock() {
        val arts = ArtifactIndexer.extract(session)
        val handoff = arts.first { it.first.endsWith("HANDOFF.md") }
        assertEquals(300.0, handoff.third, 0.001)
        val proto = arts.first { it.first.endsWith("proto_result.json") }
        assertEquals(450.0, proto.third, 0.001)
    }

    @Test fun classifyRules() {
        assertNotNull(ArtifactIndexer.classify("/x/y.md", false))
        assertNull(ArtifactIndexer.classify("/x/y", false))          // ext required for text tokens
        assertNotNull(ArtifactIndexer.classify("/x/y", true))        // strict accepts path-ish
        assertNull(ArtifactIndexer.classify("https://example.com/page", false))  // URL needs ext
        assertNotNull(ArtifactIndexer.classify("https://example.com/page.pdf", false))
        assertEquals(ArtifactKind.Images, ArtifactIndexer.classify("C:\\pics\\photo.JPG", false))
    }
}

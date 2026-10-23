package dev.momentjin.batchpracitce.practice.common.config

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReadableExecutionContextSerializerTest {
    private val serializer = ReadableExecutionContextSerializer()

    @Test
    fun `새 Context는 읽을 수 있는 JSON이며 체크포인트 값의 타입을 보존한다`() {
        val context: Map<String, Any> = hashMapOf(
            "batch.version" to "6.0.6",
            "paidOrderReader.read.count" to 25,
            "jobExecutionId" to 1L,
        )
        val output = ByteArrayOutputStream()
        serializer.serialize(context, output)
        val encoded = output.toString(Charsets.UTF_8)
        assertFalse(encoded.startsWith("rO0A"))
        assertTrue(encoded.contains("paidOrderReader.read.count"))
        JsonMapper.builder().build().readTree(encoded)
        val restored = serializer.deserialize(output.toByteArray().inputStream())
        assertEquals(context, restored)
        assertIs<Int>(restored["paidOrderReader.read.count"])
        assertIs<Long>(restored["jobExecutionId"])
    }

    @Test
    fun `기존 Base64 Context도 읽을 수 있다`() {
        val existing = "rO0ABXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAABdAANYmF0Y2gudmVyc2lvbnQABTYuMC42eA=="
        val restored = serializer.deserialize(existing.byteInputStream())
        assertEquals(mapOf("batch.version" to "6.0.6"), restored)
    }
}

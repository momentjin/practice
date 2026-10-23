package dev.momentjin.batchpracitce.practice.common.config

import org.springframework.batch.core.repository.ExecutionContextSerializer
import org.springframework.batch.core.repository.dao.DefaultExecutionContextSerializer
import org.springframework.batch.core.repository.dao.JacksonExecutionContextStringSerializer
import java.io.InputStream
import java.io.OutputStream

/** 새 Context는 JSON으로 저장하고, 이미 저장된 Java 직렬화/Base64 Context도 읽는다. */
class ReadableExecutionContextSerializer : ExecutionContextSerializer {
    private val json = JacksonExecutionContextStringSerializer()
    private val legacy = DefaultExecutionContextSerializer()

    override fun serialize(context: Map<String, Any>, outputStream: OutputStream) {
        // 기본 JSON serializer가 읽는 HashMap 형태로 고정하며 값의 타입 정보도 보존한다.
        json.serialize(HashMap(context), outputStream)
    }

    override fun deserialize(inputStream: InputStream): Map<String, Any> {
        val bytes = inputStream.readBytes()
        // 기존 Java 직렬화 스트림의 Base64 접두사. JSON 오류를 legacy로 숨기지는 않는다.
        val isLegacy = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(LEGACY_PREFIX)
        return if (isLegacy) legacy.deserialize(bytes.inputStream()) else json.deserialize(bytes.inputStream())
    }

    private companion object {
        val LEGACY_PREFIX = "rO0A".toByteArray(Charsets.US_ASCII)
    }
}

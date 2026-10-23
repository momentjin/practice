package dev.momentjin.batchpracitce.practice.practice03

import dev.momentjin.batchpracitce.practice.common.settlement.*
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("practice03")
@StepScope
class ThirdChunkFailureWriter(
    private val delegate: SettlementResultWriter,
    @Value("\${practice.force-fail:true}") private val forceFail: Boolean,
) : ItemWriter<SettlementPreview> {
    private val log = LoggerFactory.getLogger(javaClass)
    private var chunkNumber = 0
    override fun write(chunk: Chunk<out SettlementPreview>) {
        if (chunk.isEmpty) return
        chunkNumber++
        delegate.write(chunk)
        log.info("[재시작] 이번 Step 실행의 청크={}", chunkNumber)
        // 공용 Writer가 flush한 뒤, 이 청크가 commit되기 전에 실패시킨다.
        if (forceFail && chunkNumber == 3) error("세 번째 Chunk 저장 후 의도적 실패")
    }
}

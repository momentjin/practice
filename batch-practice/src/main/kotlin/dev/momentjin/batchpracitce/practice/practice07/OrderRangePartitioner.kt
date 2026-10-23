package dev.momentjin.batchpracitce.practice.practice07

import dev.momentjin.batchpracitce.practice.common.fixture.PracticeDataset
import org.springframework.batch.core.partition.Partitioner
import org.springframework.batch.infrastructure.item.ExecutionContext

class OrderRangePartitioner(private val dataset: PracticeDataset, private val total: Int = 80) : Partitioner {
    override fun partition(gridSize: Int): Map<String, ExecutionContext> {
        require(gridSize in 1..total)
        return (0 until gridSize).associate { index ->
            val first = index * total / gridSize + 1
            val last = (index + 1) * total / gridSize
            "partition${index + 1}" to ExecutionContext().apply {
                putString("fromOrderNumber", dataset.orderNumber(first))
                putString("toOrderNumber", dataset.orderNumber(last))
            }
        }
    }
}

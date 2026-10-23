package dev.momentjin.batchpracitce.practice.common.fixture

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.sql.Timestamp

/** 테스트 데이터 전용. 일반 주문 처리는 OrderService의 JPA 경로를 사용한다. */
@Service
@Profile("seed")
class OrderSeedService(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(count: Int) {
        require((jdbc.dataSource as? HikariDataSource)?.maximumPoolSize?.let { it >= 2 } != false) { "시드 실행에는 DB 풀 크기가 2 이상이어야 합니다." }
        require(count in 1..10_000_000) { "시드 주문 수는 1~10,000,000이어야 합니다." }
        // 동시 시더를 막는다. 동일 커넥션에서 획득/반납해야 하는 MySQL advisory lock을 사용한다.
        jdbc.dataSource!!.connection.use { lockConnection ->
            val acquired = lockConnection.createStatement().use { statement ->
                statement.executeQuery("SELECT GET_LOCK('batch_practice_seed_v1', 0)").use { rows ->
                    rows.next() && rows.getInt(1) == 1
                }
            }
            check(acquired) { "이미 주문 시드가 실행 중입니다." }
            try {
                seedOrders(count)
            } finally {
                lockConnection.createStatement().use { it.execute("DO RELEASE_LOCK('batch_practice_seed_v1')") }
            }
        }
    }

    private fun seedOrders(count: Int) {
        require(jdbc.dataSource != null)
        transactions.executeWithoutResult {
            for (number in 1..MERCHANT_COUNT) {
                val code = "sample-v1-merchant-%03d".format(number)
                val exists = jdbc.queryForObject("SELECT COUNT(*) FROM merchants WHERE code = ?", Long::class.java, code)!! > 0
                if (!exists) jdbc.update("INSERT INTO merchants(code, name) VALUES (?, ?)", code, "샘플 가맹점 $number")
            }
        }
        val merchantIds = jdbc.query(
            "SELECT id FROM merchants WHERE code LIKE 'sample-v1-merchant-%' ORDER BY code",
            { rs, _ -> rs.getLong("id") },
        )
        check(merchantIds.size == MERCHANT_COUNT) { "시드 가맹점 구성이 일치하지 않습니다." }

        var inserted = 0
        for (start in 1..count step CHUNK_SIZE) {
            val end = minOf(start + CHUNK_SIZE - 1, count)
            transactions.executeWithoutResult {
                val existing = jdbc.query(
                    "SELECT order_number FROM purchase_orders WHERE order_number BETWEEN ? AND ?",
                    { rs, _ -> rs.getString(1) }, orderNumber(start), orderNumber(end),
                ).toSet()
                val newNumbers = (start..end).filter { orderNumber(it) !in existing }
                if (newNumbers.isNotEmpty()) {
                    jdbc.batchUpdate(INSERT_ORDER, newNumbers, CHUNK_SIZE) { ps, number ->
                        val at = START_DATE.plusDays((number % 30).toLong()).plusSeconds((number % 86_400).toLong())
                        val status = when (number % 10) { 0 -> "CREATED"; 1 -> "CANCELLED"; else -> "PAID" }
                        ps.setString(1, orderNumber(number))
                        // 절반의 주문을 첫 번째 가맹점에 몰아 향후 파티션 쏠림도 실험한다.
                        ps.setLong(2, merchantIds[if (number % 2 == 0) 0 else 1 + (number / 2) % (MERCHANT_COUNT - 1)])
                        ps.setLong(3, 1_000L + (number % 100_000) * 100L)
                        ps.setString(4, arrayOf("APP", "WEB", "OFFLINE")[number % 3])
                        ps.setString(5, status)
                        ps.setTimestamp(6, Timestamp.valueOf(at))
                        ps.setTimestamp(7, if (status == "CREATED") null else Timestamp.valueOf(at.plusMinutes(1)))
                        ps.setTimestamp(8, if (status == "CANCELLED") Timestamp.valueOf(at.plusDays(1)) else null)
                    }
                    insertLedger("PAYMENT", "paid_at", start, end)
                    insertLedger("CANCELLATION", "cancelled_at", start, end)
                    inserted += newNumbers.size
                }
            }
            if (end % 100_000 == 0 || end == count) log.info("주문 시드 진행 {}/{} (신규 {}건)", end, count, inserted)
        }
    }

    private fun insertLedger(type: String, timeColumn: String, start: Int, end: Int) {
        jdbc.update(
            """
            INSERT INTO payment_ledgers(order_id, entry_type, amount, occurred_at)
            SELECT o.id, ?, o.amount, o.$timeColumn FROM purchase_orders o
            WHERE o.order_number BETWEEN ? AND ? AND o.$timeColumn IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM payment_ledgers l WHERE l.order_id = o.id AND l.entry_type = ?)
            """.trimIndent(), type, orderNumber(start), orderNumber(end), type,
        )
    }

    private fun orderNumber(number: Int) = "sample-v1-order-%08d".format(number)

    companion object {
        private const val MERCHANT_COUNT = 100
        private const val CHUNK_SIZE = 1_000
        private val START_DATE = LocalDateTime.of(2026, 9, 1, 0, 0)
        private const val INSERT_ORDER = """
            INSERT INTO purchase_orders(order_number, merchant_id, amount, channel, status, ordered_at, paid_at, cancelled_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
        """
    }
}

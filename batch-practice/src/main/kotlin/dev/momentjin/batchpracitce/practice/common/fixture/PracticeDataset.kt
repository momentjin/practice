package dev.momentjin.batchpracitce.practice.common.fixture

/** 번호는 업무 ID가 아니라 반복 가능한 실습 데이터의 고정 순번이다. */
data class PracticeDataset(val name: String) {
    init { require(name.matches(Regex("[a-zA-Z0-9-]{1,24}"))) { "dataset은 영문·숫자·하이픈 1~24자입니다." } }
    val merchantCode get() = "practice-$name"
    val prefix get() = "practice-$name-"
    val orderNumberLength get() = prefix.length + 5
    fun orderNumber(number: Int): String {
        require(number in 1..99_999)
        return prefix + number.toString().padStart(5, '0')
    }
    fun position(orderNumber: String): Int {
        require(orderNumber.startsWith(prefix) && orderNumber.length == orderNumberLength)
        return orderNumber.removePrefix(prefix).toInt()
    }
}

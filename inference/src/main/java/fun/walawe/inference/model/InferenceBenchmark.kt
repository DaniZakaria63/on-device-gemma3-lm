package `fun`.walawe.inference.model

data class InferenceBenchmark(
    val timeToFirstTokenMs: Long,
    val totalTimeMs: Long,
    val totalTokens: Int,
    val decodeSpeedTps: Float,
) {
    override fun toString(): String =
        "TTFT=${timeToFirstTokenMs}ms | " +
                "Total=${totalTimeMs}ms | " +
                "Tokens=$totalTokens | " +
                "Speed=${String.format("%.1f", decodeSpeedTps)} tok/s"

    companion object {
        fun empty() = InferenceBenchmark(0L, 0L, 0, 0f)
    }
}
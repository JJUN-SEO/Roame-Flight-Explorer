package travel.roame.takehome.domain

/** Points text for both apps (T1, T2). It uses US grouping on every device. */
object PointsFormat {
    /** `25,000 pts`. */
    fun full(points: Int): String = "${points.toString().reversed().chunked(3).joinToString(",").reversed()} pts"

    /** `9k`, `25k`, `101.5k`, rounding to one decimal. Values below 1,000 are shown as they are. */
    fun compact(points: Int): String {
        if (points < 1_000) return points.toString()
        val tenths = (points + 50) / 100
        return if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
    }
}

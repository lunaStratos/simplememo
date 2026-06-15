package com.lunastratos.simplememo.data

/**
 * 메모 한 건. deleted 가 0보다 크면 휴지통에 있는 상태(삭제 시각).
 * color 는 ARGB 값이며 0이면 색상 없음. position 은 직접 정렬순에서의 순서.
 * pinned 면 목록 맨 위에 고정. reminder 는 알림 시각(epoch ms), 0이면 없음.
 */
data class Memo(
    val id: Long,
    val body: String,
    val created: Long,
    val updated: Long,
    val deleted: Long,
    val color: Int,
    val position: Long,
    val pinned: Boolean,
    val reminder: Long,
) {
    /** 첫 줄을 제목으로 사용 */
    val title: String
        get() = decorate(body.trim().lineSequence().firstOrNull().orEmpty())

    /** 첫 줄 이후 내용을 한 줄 미리보기로 사용 */
    val preview: String
        get() = body.trim().lineSequence().drop(1)
            .map { decorate(it.trim()) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** 메모 본문에 포함된 #태그 목록 */
    val tags: Set<String>
        get() = TAG_REGEX.findAll(body).map { it.groupValues[1] }.toSet()

    companion object {
        val TAG_REGEX = Regex("#([\\p{L}\\p{N}_]+)")

        /** 체크리스트 마커를 목록 표시용 기호로 바꾼다 */
        fun decorate(line: String): String = when {
            line.startsWith(CHECK_DONE) -> "☑ " + line.removePrefix(CHECK_DONE).trim()
            line.startsWith(CHECK_TODO) -> "☐ " + line.removePrefix(CHECK_TODO).trim()
            else -> line
        }

        const val CHECK_TODO = "- [ ]"
        const val CHECK_DONE = "- [x]"
    }
}

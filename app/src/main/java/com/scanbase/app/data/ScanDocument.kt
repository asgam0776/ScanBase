package com.scanbase.app.data

data class ScanPage(
    val id: Long,
    val imagePath: String,
    val createdAtMillis: Long
)

data class ScanDocument(
    val id: Long,
    val title: String,
    val pages: List<ScanPage>
) {
    companion object {
        fun empty(): ScanDocument {
            return ScanDocument(
                id = System.currentTimeMillis(),
                title = "ScanBase",
                pages = emptyList()
            )
        }
    }
}

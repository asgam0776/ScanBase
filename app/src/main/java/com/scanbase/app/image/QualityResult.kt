package com.scanbase.app.image

data class QualityResult(
    val isBlurry: Boolean,
    val isTooDark: Boolean,
    val isTooBright: Boolean,
    val blurScore: Double,
    val brightness: Double,
    val warnings: List<String>
) {
    companion object {
        const val BlurThreshold = 100.0
        const val DarkThreshold = 60.0
        const val BrightThreshold = 210.0

        private const val BlurryWarning = "이미지가 흐릴 수 있습니다. 다시 촬영하면 더 선명한 결과를 얻을 수 있습니다."
        private const val DarkWarning = "이미지가 어둡습니다. 조명을 밝게 해주세요."
        private const val BrightWarning = "빛이 너무 강합니다. 반사를 줄여주세요."

        fun fromScores(
            blurScore: Double,
            brightness: Double,
            blurThreshold: Double = BlurThreshold,
            darkThreshold: Double = DarkThreshold,
            brightThreshold: Double = BrightThreshold
        ): QualityResult {
            val isBlurry = blurScore < blurThreshold
            val isTooDark = brightness < darkThreshold
            val isTooBright = brightness > brightThreshold
            val warnings = buildList {
                if (isBlurry) add(BlurryWarning)
                if (isTooDark) add(DarkWarning)
                if (isTooBright) add(BrightWarning)
            }
            return QualityResult(
                isBlurry = isBlurry,
                isTooDark = isTooDark,
                isTooBright = isTooBright,
                blurScore = blurScore,
                brightness = brightness,
                warnings = warnings
            )
        }
    }
}

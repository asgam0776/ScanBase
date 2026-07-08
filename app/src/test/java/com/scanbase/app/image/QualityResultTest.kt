package com.scanbase.app.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityResultTest {
    @Test
    fun createsWarningsForBlurryDarkAndBrightStates() {
        val blurry = QualityResult.fromScores(blurScore = 70.0, brightness = 120.0)
        val dark = QualityResult.fromScores(blurScore = 140.0, brightness = 50.0)
        val bright = QualityResult.fromScores(blurScore = 140.0, brightness = 220.0)

        assertTrue(blurry.isBlurry)
        assertEquals("이미지가 흐릴 수 있습니다. 다시 촬영하면 더 선명한 결과를 얻을 수 있습니다.", blurry.warnings.single())

        assertTrue(dark.isTooDark)
        assertEquals("이미지가 어둡습니다. 조명을 밝게 해주세요.", dark.warnings.single())

        assertTrue(bright.isTooBright)
        assertEquals("빛이 너무 강합니다. 반사를 줄여주세요.", bright.warnings.single())
    }

    @Test
    fun createsNoWarningsForAcceptableScores() {
        val result = QualityResult.fromScores(blurScore = 140.0, brightness = 120.0)

        assertFalse(result.isBlurry)
        assertFalse(result.isTooDark)
        assertFalse(result.isTooBright)
        assertTrue(result.warnings.isEmpty())
    }
}

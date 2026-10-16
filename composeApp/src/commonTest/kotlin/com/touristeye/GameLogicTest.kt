package com.touristeye

import com.touristeye.game.Detection
import com.touristeye.game.FillCenterMapping
import com.touristeye.game.GameLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameLogicTest {
    @Test
    fun fillCenterCropsTheLongSide() {
        // 480x640 image on a 1080x2400 screen: scaled by 3.75 to 1800x2400, 360 px cut from each side.
        val m = FillCenterMapping(480f, 640f, 1080f, 2400f)
        val center = m.toView(0.5f, 0.5f)
        assertEquals(540f, center.x, 0.01f)
        assertEquals(1200f, center.y, 0.01f)
        assertEquals(-360f, m.toView(0f, 0f).x, 0.01f)
        val back = m.toImage(540f, 1200f)
        assertEquals(0.5f, back.x, 0.0001f)
        assertEquals(0.5f, back.y, 0.0001f)
        assertEquals(0.2f, m.toImage(0f, 0f).x, 0.0001f)
    }

    @Test
    fun smallestBoxUnderTheCrosshairWins() {
        val car = Detection("car", 0.9f, 0.1f, 0.1f, 0.9f, 0.9f)
        val dog = Detection("dog", 0.7f, 0.4f, 0.4f, 0.6f, 0.6f)
        assertEquals(dog, GameLogic.targetAt(listOf(car, dog), 0.5f, 0.5f))
        assertEquals(car, GameLogic.targetAt(listOf(car, dog), 0.2f, 0.2f))
        assertNull(GameLogic.targetAt(listOf(car, dog), 0.95f, 0.5f))
    }

    @Test
    fun pointsKnowTheLabels() {
        assertEquals(10, GameLogic.points("person"))
        assertEquals(15, GameLogic.points("Tree"))
        assertEquals(5, GameLogic.points("Sky"))
        assertEquals("Potted plant", GameLogic.displayName("potted plant"))
    }
}

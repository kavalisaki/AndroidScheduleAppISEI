package com.example.iseuschedule.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun testDarkThemeContrastAndMutedElements() {
        val darkSurfaceLum = DarkColorScheme.surface.luminance()
        val darkOnSurfaceLum = DarkColorScheme.onSurface.luminance()

        // Surface must be dark (luminance < 0.1)
        assertTrue("Dark surface luminance should be very low", darkSurfaceLum < 0.1f)
        // onSurface must be high contrast (luminance > 0.8) for maximum readability
        assertTrue("Dark onSurface luminance should be high", darkOnSurfaceLum > 0.8f)

        // Verify that secondary/tertiary containers are deeply subdued (not bright glowing colors)
        assertTrue("secondaryContainer should be dark", DarkColorScheme.secondaryContainer.luminance() < 0.15f)
        assertTrue("tertiaryContainer should be dark", DarkColorScheme.tertiaryContainer.luminance() < 0.15f)

        // Verify surfaceVariant is muted
        assertTrue("surfaceVariant should be dark", DarkColorScheme.surfaceVariant.luminance() < 0.15f)

        // onSurfaceVariant and outline should have lower luminance than onSurface
        assertTrue(
            "onSurfaceVariant should be softer than onSurface",
            DarkColorScheme.onSurfaceVariant.luminance() < darkOnSurfaceLum
        )
        assertTrue(
            "outline should be softer than onSurface",
            DarkColorScheme.outline.luminance() < darkOnSurfaceLum
        )
    }

    @Test
    fun testLightThemeReadabilityPreserved() {
        val lightSurfaceLum = LightColorScheme.surface.luminance()
        val lightOnSurfaceLum = LightColorScheme.onSurface.luminance()

        // Surface must be light (luminance > 0.9)
        assertTrue("Light surface luminance should be high", lightSurfaceLum > 0.9f)
        // onSurface must be dark (luminance < 0.1)
        assertTrue("Light onSurface luminance should be low", lightOnSurfaceLum < 0.1f)

        // Light primary / background unchanged
        assertEquals(Color(0xFFFFFBFE), LightColorScheme.background)
        assertEquals(Color(0xFF1C1B1F), LightColorScheme.onSurface)
    }
}

package com.example.multibarcode.ui.components

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy

/**
 * High-resolution image-analysis config for much sharper barcode reading.
 *
 * The default analysis resolution is ~640×480; that is too coarse to read small or many-at-once
 * codes reliably. We ask for 1920×1080 (≈6× the pixels), falling back to the closest supported
 * size on devices that can't do exactly 1080p.
 */
internal fun highResSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(1920, 1080),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
        )
        .build()

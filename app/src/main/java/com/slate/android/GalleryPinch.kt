// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

/** Discrete density steps with a dead zone to avoid accidental zoom on small movements. */
internal fun galleryPinchStep(scale:Float):Int=when{
    !scale.isFinite() || scale<=0f->0
    scale>=1.22f->-1
    scale<=1f/1.22f->1
    else->0
}

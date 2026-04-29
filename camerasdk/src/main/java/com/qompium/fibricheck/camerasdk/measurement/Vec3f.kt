package com.qompium.fibricheck.camerasdk.measurement

import com.qompium.fibricheck.camerasdk.extensions.differs

data class Vec3f(
    val r: Float = 0.0f,
    val g: Float = 0.0f,
    val b: Float = 0.0f
) {
    override fun toString(): String {
        return "r: $r, g: $g, b: $b"
    }

    fun differs(other: Vec3f, delta: Float = 0.001f): Boolean {
        return other.r.differs(r, delta) || other.g.differs(g, delta) || other.b.differs(b, delta)
    }
}

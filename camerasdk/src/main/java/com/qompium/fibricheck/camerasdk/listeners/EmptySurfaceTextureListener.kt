package com.qompium.fibricheck.camerasdk.listeners

import android.graphics.SurfaceTexture
import android.view.TextureView.SurfaceTextureListener

/**
 * Simple wrapper to reduce the boilerplate in FibriCheckerImpl2
 */
open class EmptySurfaceTextureListener: SurfaceTextureListener {
  /**
   * Invoked when a [TextureView]'s SurfaceTexture is ready for use.
   *
   * @param surface The surface returned by
   * [android.view.TextureView.getSurfaceTexture]
   * @param width The width of the surface
   * @param height The height of the surface
   */
  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {

  }

  /**
   * Invoked when the [SurfaceTexture]'s buffers size changed.
   *
   * @param surface The surface returned by
   * [android.view.TextureView.getSurfaceTexture]
   * @param width The new width of the surface
   * @param height The new height of the surface
   */
  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

  }

  /**
   * Invoked when the specified [SurfaceTexture] is about to be destroyed.
   * If returns true, no rendering should happen inside the surface texture after this method
   * is invoked. If returns false, the client needs to call [SurfaceTexture.release].
   * Most applications should return true.
   *
   * @param surface The surface about to be destroyed
   */
  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    return true
  }

  /**
   * Invoked when the specified [SurfaceTexture] is updated through
   * [SurfaceTexture.updateTexImage].
   *
   * @param surface The surface just updated
   */
  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

  }
}
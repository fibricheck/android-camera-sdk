package com.qompium.fibricheck.camerasdk.extensions

import android.graphics.ImageFormat
import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi

import android.graphics.Rect
import android.util.Log

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private fun imageToByteArray(image: Image): ByteArray {
  assert(image.format == ImageFormat.YUV_420_888)

  val pixelCount = image.cropRect.width() * image.cropRect.height()
  // Bits per pixel is an average for the whole image, so it's useful to compute the size
  // of the full buffer but should not be used to determine pixel offsets
  val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
  val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)

  val imageCrop = image.cropRect
  val imagePlanes = image.planes

  imagePlanes.forEachIndexed { planeIndex, plane ->
    var outputOffset = when (planeIndex) {
      // Y plane is the first chunk
      // There is 1 byte per pixel in Y plane
      // ySize = width * height
      0 -> 0
      // U plane is the second chunk
      // The U plane is in half resolution of the Y plane
      // uSize = (width / 2)  * (height  *  2)
      // uSize = (width * height) / 4
      // It starts after the Y plane
      // uStart = ySize
      1 -> pixelCount
      // V plane is the second chunk
      // The V plane is in half resolution of the Y Plane
      // vSize = (width / 2)  * (height  *  2)
      // vSize = (width * height) / 4
      // It starts after the Y plane
      // vStart = ySize + uSize
      2 -> pixelCount + pixelCount / 4
      else -> {
        println("Invalid plane index $planeIndex")
        return@forEachIndexed
      }
    }

    val planeBuffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    // We have to divide the width and height by two if it's not the Y plane
    val planeCrop = if (planeIndex == 0) {
      imageCrop
    } else {
      Rect(
        imageCrop.left / 2,
        imageCrop.top / 2,
        imageCrop.right / 2,
        imageCrop.bottom / 2
      )
    }

    val planeWidth = planeCrop.width()
    val planeHeight = planeCrop.height()

    // Intermediate buffer used to store the bytes of each row
    val rowBuffer = ByteArray(plane.rowStride)

    // Size of each row in bytes
    val rowLength = if (pixelStride == 1) {
      planeWidth
    } else {
      // Take into account that the stride may include data from pixels other than this
      // particular plane and row, and that could be between pixels and not after every
      // pixel:
      //
      // |---- Pixel stride ----|                    Row ends here --> |
      // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
      //
      // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
      (planeWidth - 1) * pixelStride + 1
    }

    for (row in 0 until planeHeight) {
      // Move buffer position to the beginning of this row
      planeBuffer.position(
        (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride)

      if (pixelStride == 1) {
        // When there is a single stride value for pixel and output, we can just copy
        // the entire row in a single step
        planeBuffer.get(outputBuffer, outputOffset, rowLength)
        outputOffset += rowLength
      } else {
        // When either pixel or output have a stride > 1 we must copy pixel by pixel
        planeBuffer.get(rowBuffer, 0, rowLength)
        for (col in 0 until planeWidth) {
          outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
          outputOffset += 1
        }
      }
    }
  }

  return outputBuffer
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun Image.toByteArray(): ByteArray {
  if (format == ImageFormat.YUV_420_888) {
    return imageToByteArray(this)
  }

  throw Exception("Invalid image format")
}

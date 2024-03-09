import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class CarAnalyzer(val diffThreshold: Double, private val onCarDetected: () -> Unit) : ImageAnalysis.Analyzer {

    private var lastFrame: Mat? = null

    private fun imageProxyToMat(image: ImageProxy): Mat? {
        if (image.format == ImageFormat.YUV_420_888) {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val ySize = yPlane.buffer.remaining()
            val uSize = uPlane.buffer.remaining()
            val vSize = vPlane.buffer.remaining()

            val data = ByteArray(ySize + uSize + vSize)

            yPlane.buffer.get(data, 0, ySize)
            vPlane.buffer.get(data, ySize, vSize)
            uPlane.buffer.get(data, ySize + vSize, uSize)

            val matYuv = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
            matYuv.put(0, 0, data)

            val matRgb = Mat()
            Imgproc.cvtColor(matYuv, matRgb, Imgproc.COLOR_YUV2RGB_NV21, 3)

            matYuv.release()
            return matRgb
        } else {
            Log.e("CarAnalyzer", "Unsupported image format: ${image.format}")
        }
        return null
    }

    private fun detectCar(currentFrame: Mat): Boolean {
        lastFrame?.let { lastFrame ->
            val diff = Mat()
            Core.absdiff(lastFrame, currentFrame, diff)
            Imgproc.cvtColor(diff, diff, Imgproc.COLOR_BGR2GRAY)
            Core.normalize(diff, diff, 0.0, 255.0, Core.NORM_MINMAX)
            val mean = Core.mean(diff).`val`[0]
            diff.release()
            Log.d("CarAnalyzer", "Mean diff: $mean")
            return mean > diffThreshold
        }
        return false
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentFrame = imageProxyToMat(imageProxy)
        if (currentFrame != null) {
            if (detectCar(currentFrame)) {
                onCarDetected()
            }

            // Update the last frame reference
            lastFrame?.release()
            lastFrame = currentFrame
        }
    }

    fun reset() {
        lastFrame?.release()
        lastFrame = null
    }
}

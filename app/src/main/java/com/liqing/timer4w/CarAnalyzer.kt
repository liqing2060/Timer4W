import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class CarAnalyzer(val diffThreshold: Double, private val onCarDetected: () -> Unit) : ImageAnalysis.Analyzer {

    private var lastFrame: Mat? = null

    private fun imageProxyToMat(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer // Y
        val ySize = yBuffer.remaining()
        val yData = ByteArray(ySize)
        yBuffer.get(yData)
        val matY = Mat(image.height, image.width, CvType.CV_8UC1)
        matY.put(0, 0, yData)

        val uvBuffer = image.planes[1].buffer // UV
        val uvSize = uvBuffer.remaining()
        val uvData = ByteArray(uvSize)
        uvBuffer.get(uvData)
        val matUV = Mat(image.height / 2, image.width / 2, CvType.CV_8UC2)
        matUV.put(0, 0, uvData)

        val mat = Mat()
        Imgproc.cvtColorTwoPlane(matY, matUV, mat, Imgproc.COLOR_YUV2BGR_NV21)

        matY.release()
        matUV.release()

        return mat
    }

    private fun detectCar(currentFrame: Mat): Boolean {
        lastFrame?.let { lastFrame ->
            val diff = Mat()
            Core.absdiff(lastFrame, currentFrame, diff)
            Imgproc.cvtColor(diff, diff, Imgproc.COLOR_BGR2GRAY)
            Core.normalize(diff, diff, 0.0, 255.0, Core.NORM_MINMAX)
            val mean = Core.mean(diff).`val`[0]
            diff.release()
            return mean > diffThreshold
        }
        return false
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentFrame = imageProxyToMat(imageProxy)
        if (detectCar(currentFrame)) {
            onCarDetected()
        }

        // Update the last frame reference
        lastFrame?.release()
        lastFrame = currentFrame
    }
}

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video

class CarAnalyzer(
    val diffThreshold: Double,
    private val onCarDetected: () -> Unit,
    private val backgroundSubtractor: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2()
) : ImageAnalysis.Analyzer {

    private var lastFrame: Mat? = null
    private var prevForeground: Mat? = null
    var curDiff = mutableDoubleStateOf(0.0)

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
            curDiff.doubleValue = mean
            Log.d("CarAnalyzer", "Mean diff: " + "%.2f".format(curDiff.doubleValue))
            return mean > diffThreshold
        }
        return false
    }

    private fun detectCarAdvanced(currentFrame: Mat): Boolean {
        // 转换当前帧为灰度图
        val grayFrame = Mat()
        Imgproc.cvtColor(currentFrame, grayFrame, Imgproc.COLOR_BGR2GRAY)

        // 步骤1：背景减除
        val fgMask = Mat()
        backgroundSubtractor.apply(grayFrame, fgMask)

        // 步骤2：提取前景
        val foreground = Mat()
        grayFrame.copyTo(foreground, fgMask)

        // 步骤3：光流法计算前景区域的光流
        if (prevForeground == null) {
            prevForeground = foreground.clone()
            return false
        }

        val flow = Mat()
        // 确保前一帧前景和当前帧前景大小一致
        if (prevForeground!!.size() != foreground.size()) {
            // 处理大小不一致的情况
            return false
        }
        // 应用光流法
        Video.calcOpticalFlowFarneback(prevForeground, foreground, flow, 0.5, 3, 15, 3, 5, 1.2, 0)

        // 步骤4：分析光流结果，判断四驱车运动
        val ret = analyzeOpticalFlow(flow)

        // 更新前一帧的前景
        prevForeground!!.release() // 释放旧的前景内存
        prevForeground = foreground // 更新前景

        // 释放资源
        fgMask.release()
        grayFrame.release()
        flow.release()

        return ret
    }

    private fun analyzeOpticalFlow(flow: Mat) : Boolean {
        // flow 是一个包含了运动向量的 Mat，其中 flow.get(y, x) 返回一个包含 dx 和 dy 的 double 数组，表示该点的运动。

        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        // 遍历光流场，计算所有向量的平均值
        for (y in 0 until flow.rows()) {
            for (x in 0 until flow.cols()) {
                val vector = flow.get(y, x)
                sumX += vector[0] // dx
                sumY += vector[1] // dy
                count++
            }
        }

        val avgX = sumX / count
        val avgY = sumY / count
        val avgMotionMagnitude = Math.sqrt(avgX * avgX + avgY * avgY) // 平均运动向量的大小，可以用来估计速度
        curDiff.doubleValue = avgMotionMagnitude

        Log.d("CarAnalyzer", "avgMotionMagnitude: " + "%.2f".format(curDiff.doubleValue))

        // 根据平均运动向量的大小决定是否检测到高速通过的四驱车
        if (avgMotionMagnitude >= diffThreshold) {
            // 如果平均速度超过阈值，认为检测到四驱车高速通过
            Log.d("CarAnalyzer", "Detected high-speed moving car.")
            return true;
        }

        return false
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentFrame = imageProxyToMat(imageProxy)
        if (currentFrame != null) {
//            if (detectCar(currentFrame)) {
//                onCarDetected()
//            }
            if (detectCarAdvanced(currentFrame)) {
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
        prevForeground?.release()
        prevForeground = null;
        curDiff.doubleValue = 0.0
    }
}

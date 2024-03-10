import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video

class CarAnalyzer(
    val diffThreshold: Double,
    private val onCarDetected: () -> Unit,
    private val onImageAnalysis: () -> Unit? = { null },
    private val backgroundSubtractor: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2()
) : ImageAnalysis.Analyzer {

    private var lastFrame: Mat? = null
    private var prevForeground: Mat? = null
    var speed = mutableDoubleStateOf(0.0)
    var backgroundDiff = mutableDoubleStateOf(0.0)
    var carPassedThisFrame = mutableStateOf(false)
    private val skippedFrameCount = mutableStateOf(0)

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
            speed.doubleValue = mean
            carPassedThisFrame.value = mean > diffThreshold
            Log.d("CarAnalyzer", "Mean diff: " + "%.2f".format(speed.doubleValue) + ", carPassedThisFrame: " + carPassedThisFrame.value)
            onImageAnalysis()
            return carPassedThisFrame.value
        }
        return false
    }

    private fun detectCarAdvanced(currentFrame: Mat): Boolean {

        val resizedFrame = Mat()
        val scaleFactor = 0.5 // 缩小到50%的分辨率
        Imgproc.resize(
            currentFrame,
            resizedFrame,
            Size(),
            scaleFactor,
            scaleFactor,
            Imgproc.INTER_AREA
        )
//        val resizedFrame = currentFrame

        // 转换当前帧为灰度图
        val grayFrame = Mat()
        Imgproc.cvtColor(resizedFrame, grayFrame, Imgproc.COLOR_BGR2GRAY)
        resizedFrame.release()

        // 步骤1：背景减除
        val fgMask = Mat()
        backgroundSubtractor.apply(grayFrame, fgMask)

        // 步骤2：计算整体变化量
        backgroundDiff.doubleValue =
            Core.countNonZero(fgMask).toDouble() / (fgMask.rows() * fgMask.cols())

        // 步骤3：提取前景
        val foreground = Mat()
        grayFrame.copyTo(foreground, fgMask)

        // 用于形态学操作的核
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        // 腐蚀
        Imgproc.erode(fgMask, fgMask, kernel)
        // 膨胀
        Imgproc.dilate(fgMask, fgMask, kernel)

        // 如果背景变化太大，可能是因为拿着相机移动
        val bgChangeThreshold = 0.2
        skippedFrameCount.value += 1
        if (prevForeground == null || backgroundDiff.doubleValue > bgChangeThreshold || skippedFrameCount.value <= 30) {
            prevForeground?.release() // 释放前一帧前景图像资源
            prevForeground = foreground.clone()
            fgMask.release()
            grayFrame.release()
            return false // 跳过运动分析
        }

        val flow = Mat()
        Video.calcOpticalFlowFarneback(prevForeground, foreground, flow, 0.5, 3, 15, 3, 5, 1.2, 0)

        // 分析光流结果，判断四驱车运动
        carPassedThisFrame.value = analyzeOpticalFlow(flow)
        onImageAnalysis()
        flow.release()
        prevForeground!!.release() // 释放前一帧前景资源
        prevForeground = foreground // 更新前一帧为当前帧

        fgMask.release()
        grayFrame.release()
        return carPassedThisFrame.value
    }

    private fun analyzeOpticalFlow(flow: Mat): Boolean {
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
        speed.doubleValue = avgMotionMagnitude
        carPassedThisFrame.value = avgMotionMagnitude >= diffThreshold

        Log.d("CarAnalyzer", "speed: " + "%.2f".format(speed.doubleValue) + ", carPassedThisFrame: " + carPassedThisFrame.value)

        // 根据平均运动向量的大小决定是否检测到高速通过的四驱车
        if (carPassedThisFrame.value) {
            // 如果平均速度超过阈值，认为检测到四驱车高速通过
            Log.d("CarAnalyzer", "Detected high-speed moving car.")
        }

        return carPassedThisFrame.value
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
        speed.doubleValue = 0.0
        backgroundDiff.doubleValue = 0.0
        skippedFrameCount.value = 0
    }
}

package com.balance.classreminder.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用 ML Kit 的中文识别（bundled 版，模型打进 APK，不走 Google Play 服务）把图片转成文本行。
 * 只负责"图片 → 带坐标的文本行"，怎么还原成课表交给 GridParser。
 */
object TimetableOcr {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(context: Context, uri: Uri): List<OcrBox> {
        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val boxes = ArrayList<OcrBox>()
                    text.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val box = line.boundingBox
                            if (box != null && line.text.isNotBlank()) {
                                boxes += OcrBox(line.text.trim(), box.left, box.top, box.right, box.bottom)
                            }
                        }
                    }
                    if (cont.isActive) cont.resume(boxes)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }
}

package com.dementor.caregiver.data.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

enum class ChatIntent {
    EMERGENCY, MEDICAL_QUERY, CASUAL, UNKNOWN
}

class IntentClassifier(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = Interpreter(loadModelFile(context, "intent_classifier.tflite"))
        } catch (e: Exception) {
            e.printStackTrace() // Expected to fail until you drop the .tflite file in assets
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classifyIntent(text: String): ChatIntent {
        if (interpreter == null) {
            // Fallback heuristics if the LiteRT model isn't in the assets folder yet
            return when {
                text.contains("help", ignoreCase = true) || text.contains("emergency", ignoreCase = true) -> ChatIntent.EMERGENCY
                text.contains("med", ignoreCase = true) || text.contains("pill", ignoreCase = true) -> ChatIntent.MEDICAL_QUERY
                else -> ChatIntent.CASUAL
            }
        }
        
        // TODO: Actual tokenization and Interpreter inference goes here when the model is ready
        return ChatIntent.CASUAL
    }
}

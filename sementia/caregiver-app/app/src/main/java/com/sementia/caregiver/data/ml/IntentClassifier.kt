package com.sementia.caregiver.data.ml

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
            e.printStackTrace()
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

    /**
     * Classifies user input locally using LiteRT.
     * In a real implementation, this would involve tokenizing the string 
     * and running it through the model.
     */
    fun classifyIntent(text: String): ChatIntent {
        if (interpreter == null) return ChatIntent.UNKNOWN

        // Heuristic fallback for demonstration if the model is just a stub
        return when {
            text.contains("help", ignoreCase = true) || text.contains("emergency", ignoreCase = true) -> 
                ChatIntent.EMERGENCY
            text.contains("med", ignoreCase = true) || text.contains("pill", ignoreCase = true) -> 
                ChatIntent.MEDICAL_QUERY
            else -> ChatIntent.CASUAL
        }
    }
}

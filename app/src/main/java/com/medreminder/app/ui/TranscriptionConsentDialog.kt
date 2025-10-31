package com.medreminder.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.medreminder.app.R

/**
 * Dialog to request user consent for downloading Whisper AI model for transcription.
 * Shows information about model size, requirements, and usage.
 */
@Composable
fun TranscriptionConsentDialog(
    currentLanguage: String = "en",
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val title = when (currentLanguage) {
        "hi" -> "ऑडियो नोट्स ट्रांसक्रिप्शन"
        "gu" -> "ઑડિઓ નોટ્સ ટ્રાન્સક્રિપ્શન"
        "mr" -> "ऑडिओ नोट्स ट्रान्सक्रिप्शन"
        else -> "Audio Notes Transcription"
    }

    val message = when (currentLanguage) {
        "hi" -> """
            यह ऐप आपके ऑडियो नोट्स को टेक्स्ट में बदल सकता है। इसके लिए:

            • 75MB AI मॉडल डाउनलोड (केवल एक बार)
            • WiFi कनेक्शन आवश्यक
            • डिवाइस चार्जिंग पर होना चाहिए
            • सभी प्रोसेसिंग आपके डिवाइस पर होगी

            क्या आप जारी रखना चाहते हैं?
        """.trimIndent()
        "gu" -> """
            આ એપ્લિકેશન તમારી ઑડિઓ નોટ્સને ટેક્સ્ટમાં રૂપાંતરિત કરી શકે છે। આ માટે જરૂરી:

            • 75MB AI મોડેલ ડાઉનલોડ (માત્ર એક વખત)
            • WiFi કનેક્શન જરૂરી
            • ડિવાઇસ ચાર્જિંગ પર હોવું જોઈએ
            • તમામ પ્રોસેસિંગ તમારા ડિવાઇસ પર થશે

            શું તમે ચાલુ રાખવા માંગો છો?
        """.trimIndent()
        "mr" -> """
            हे अ‍ॅप तुमच्या ऑडिओ नोट्सचे मजकूरात रूपांतर करू शकते. यासाठी आवश्यक:

            • 75MB AI मॉडेल डाउनलोड (फक्त एकदा)
            • WiFi कनेक्शन आवश्यक
            • डिव्हाइस चार्जिंगवर असावे
            • सर्व प्रोसेसिंग तुमच्या डिव्हाइसवर होईल

            तुम्ही सुरू ठेवू इच्छिता?
        """.trimIndent()
        else -> """
            This app can transcribe your audio notes to text. This requires:

            • 75MB AI model download (one-time only)
            • WiFi connection required
            • Device must be charging
            • All processing happens on your device

            Do you want to continue?
        """.trimIndent()
    }

    val acceptText = when (currentLanguage) {
        "hi" -> "हाँ, शुरू करें"
        "gu" -> "હા, શરૂ કરો"
        "mr" -> "होय, सुरू करा"
        else -> "Yes, Enable"
    }

    val declineText = when (currentLanguage) {
        "hi" -> "नहीं, धन्यवाद"
        "gu" -> "ના, આભાર"
        "mr" -> "नाही, धन्यवाद"
        else -> "No, Thanks"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(acceptText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(declineText)
            }
        }
    )
}

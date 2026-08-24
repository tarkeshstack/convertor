package com.tarkeshstack.speakeasy.interpret

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tarkeshstack.speakeasy.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun Language.toMlKit(): String = when (this) {
    Language.English -> TranslateLanguage.ENGLISH
    Language.Hindi -> TranslateLanguage.HINDI
    Language.Tamil -> TranslateLanguage.TAMIL
    Language.Spanish -> TranslateLanguage.SPANISH
    Language.French -> TranslateLanguage.FRENCH
}

/**
 * Detects the language of a spoken transcript and translates it, entirely on-device via
 * Google's ML Kit — no API key, no account, and no server of ours. Each language pair's
 * small translation model downloads once (needs a network connection that first time)
 * and is then reused fully offline.
 */
class TranslationService {

    data class TranslationResult(val detectedLanguage: Language, val translatedText: String)

    suspend fun translate(text: String, sourceHint: Language?, targetLanguage: Language): Result<TranslationResult> =
        withContext(Dispatchers.Default) {
            try {
                val source = sourceHint ?: detectLanguage(text) ?: Language.English

                if (source == targetLanguage) {
                    return@withContext Result.success(TranslationResult(source, text))
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(source.toMlKit())
                    .setTargetLanguage(targetLanguage.toMlKit())
                    .build()
                val translator = Translation.getClient(options)
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
                    val translated = translator.translate(text).awaitTask()
                    Result.success(TranslationResult(source, translated))
                } finally {
                    translator.close()
                }
            } catch (e: Exception) {
                Result.failure(
                    Exception(
                        "Couldn't translate that. The first time you use a language pair, " +
                            "it needs an internet connection to download a small offline model.",
                        e,
                    ),
                )
            }
        }

    private suspend fun detectLanguage(text: String): Language? {
        val identifier = LanguageIdentification.getClient()
        return try {
            val code = identifier.identifyLanguage(text).awaitTask()
            Language.entries.find { it.toMlKit() == code }
        } catch (e: Exception) {
            null
        } finally {
            identifier.close()
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    }

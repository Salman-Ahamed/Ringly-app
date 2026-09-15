package com.ringly.app.dialer

import com.ringly.app.overlay.BestMatchSelector
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.util.NumberNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OutgoingContactLookup(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val lookup: suspend (String) -> Result<LookupResponse>,
    private val localNumbers: () -> Set<String>,
    private val selectBest: (List<LookupMatch>) -> LookupMatch? = BestMatchSelector::selectBest,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onResult: (LookupMatch?) -> Unit
) {

    private var job: Job? = null

    fun onDigitsChanged(raw: String) {
        job?.cancel()
        val normalized = NumberNormalizer.normalize(raw)
        if (normalized == null || localNumbers().contains(normalized)) {
            onResult(null)
            return
        }
        job = scope.launch {
            delay(debounceMillis)
            val response = runCatching { lookup(normalized) }
                .getOrElse { Result.failure(it) }
                .getOrNull()
            val match = response
                ?.takeIf { it.found }
                ?.matches
                ?.let(selectBest)
            onResult(match)
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        onResult(null)
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
    }
}
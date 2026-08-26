package org.beesearch.app.domain.validation

import org.beesearch.app.domain.model.InvalidObserverCodeException

object ObserverCode {
    fun normalize(value: String): String = value.trim().also {
        if (it.isEmpty()) {
            throw InvalidObserverCodeException()
        }
    }
}

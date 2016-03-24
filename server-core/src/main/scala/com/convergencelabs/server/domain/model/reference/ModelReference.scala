package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.SessionKey

class ModelReference(
    val modelValue: RealTimeValue,
    val key: String,
    val sessionId: String) {
}

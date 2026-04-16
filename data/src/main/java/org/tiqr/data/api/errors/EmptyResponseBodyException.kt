package org.tiqr.data.api.errors

class EmptyResponseBodyException(
    forRequest: String = "",
    cause: Throwable? = null,
) : Throwable("Empty response body: $forRequest", cause)
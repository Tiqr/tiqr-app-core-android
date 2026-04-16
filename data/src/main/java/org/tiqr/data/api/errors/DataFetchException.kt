package org.tiqr.data.api.errors

/**
 * UI level exception: any failure to retrieve the data is mapped to this exception at the UI level
 * */
class DataFetchException(
    message: String? = null,
    cause: Throwable? = null,
) : Throwable(message = message.orEmpty(), cause = cause)
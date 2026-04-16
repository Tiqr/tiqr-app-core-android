package org.tiqr.data.api.errors

class DataParsingException(
    message: String? = null,
    cause: Throwable? = null,
) : Throwable(message = message.orEmpty(), cause = cause)

const val FAILURE_TO_PARSE = 460
val INVALID_DATA_STRUCTURE = """
{   "status":$FAILURE_TO_PARSE,
    "title":"Unexpected response: Failed to parse response."
}
""".trimMargin()
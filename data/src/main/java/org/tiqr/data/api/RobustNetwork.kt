package org.tiqr.data.api

import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import org.tiqr.data.api.errors.DataParsingException
import org.tiqr.data.api.errors.EmptyResponseBodyException
import org.tiqr.data.api.errors.FAILURE_TO_PARSE
import org.tiqr.data.api.errors.INVALID_DATA_STRUCTURE
import retrofit2.Response
import timber.log.Timber

inline fun <reified T> makeApiCall(block: () -> Response<T>): Response<T> = try {
    val apiResponse: Response<T> = block()
    apiResponse
} catch (e: SerializationException) {
    Timber.e(e, "Failed to parse response")
    Response.error(FAILURE_TO_PARSE, INVALID_DATA_STRUCTURE.toResponseBody())
}

inline fun <reified T> processResponse(response: Response<out T>): Result<T> = with(response) {
    if (isSuccessful) {
        val data: T? = body()
        if (data != null) {
            Result.success(data)
        } else {
            Timber.e("Expected data in response body, but body is empty.")
            val cause = EmptyResponseBodyException().fillInStackTrace()
            Result.failure(cause)
        }
    } else {
        val requestFor = response.raw().request.url
        val errorCode = code()
        val exception = "$errorCode/${message()} : ${errorBody()?.string()}"
        val cause = if (errorCode == FAILURE_TO_PARSE) {
            DataParsingException(exception).fillInStackTrace()
        } else {
            IOException(exception).fillInStackTrace()
        }
        Timber.e(cause, "Failed to get ${T::class.java.name} at $requestFor. Exception: $exception")
        Result.failure(cause)
    }
}
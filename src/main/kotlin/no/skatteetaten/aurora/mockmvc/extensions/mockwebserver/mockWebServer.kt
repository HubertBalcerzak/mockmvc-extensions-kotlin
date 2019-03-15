package no.skatteetaten.aurora.mockmvc.extensions.mockwebserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.JsonPath
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

private fun MockWebServer.enqueueJson(
    status: Int = 200,
    body: Any,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    val json = body as? String ?: objectMapper.writeValueAsString(body)
    val response = MockResponse()
        .setResponseCode(status)
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
        .setBody(json)
    this.enqueue(response)
}

private fun MockWebServer.execute(fn: () -> Unit): RecordedRequest {
    try {
        fn()
        return this.takeRequest()
    } catch (t: Throwable) {
        this.takeRequest()
        throw t
    }
}

fun MockWebServer.execute(
    status: Int,
    response: Any,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
    fn: () -> Unit
): RecordedRequest {
    this.enqueueJson(status, response, objectMapper)
    return this.execute(fn)
}

fun MockWebServer.execute(response: MockResponse, fn: () -> Unit): RecordedRequest {
    this.enqueue(response)
    return this.execute(fn)
}

fun MockWebServer.execute(
    response: Any,
    fn: () -> Unit
): RecordedRequest {
    this.enqueueJson(body = response)
    return this.execute(fn)
}

fun MockWebServer.execute(vararg responses: Any, fn: () -> Unit): List<RecordedRequest> {
    fun takeRequests() = (1..responses.size).toList().map { this.takeRequest() }

    try {
        responses.forEach { this.enqueueJson(body = it) }
        fn()
        return takeRequests()
    } catch (t: Throwable) {
        takeRequests()
        throw t
    }
}

fun MockResponse.setJsonFileAsBody(fileName: String): MockResponse {
    val classPath = ClassPathResource("/$fileName")
    val json = classPath.file.readText()
    this.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
    return this.setBody(json)
}

inline fun <reified T> RecordedRequest.bodyAsObject(path: String = "$"): T {
    val content: Any = JsonPath.parse(String(body.readByteArray())).read(path)
    return jacksonObjectMapper().convertValue(content)
}

fun RecordedRequest.bodyAsString() = this.body.readUtf8()
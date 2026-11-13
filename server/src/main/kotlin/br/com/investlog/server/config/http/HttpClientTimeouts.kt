package br.com.investlog.server.config.http

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

internal fun RestClient.Builder.withTimeouts(connectTimeout: Duration, readTimeout: Duration): RestClient.Builder {
    val httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build()
    val jdkRequestFactory = JdkClientHttpRequestFactory(httpClient)
    jdkRequestFactory.setReadTimeout(readTimeout)
    return requestFactory(jdkRequestFactory)
}

package com.burpai.scanner

import burp.api.montoya.http.message.requests.HttpRequest
import java.net.URI

object InjectionPointExtractor {
    private val jsonFieldPattern = Regex("\"([A-Za-z0-9_\\-]+)\"\\s*:\\s*(\"([^\"]*)\"|(-?\\d+(?:\\.\\d+)?))")
    private val xmlElementPattern = Regex("<([A-Za-z0-9_\\-]+)>([^<]{1,200})</\\1>")
    private val pathIdPattern = Regex("([0-9]+|[a-f0-9-]{36}|[a-f0-9]{24})", RegexOption.IGNORE_CASE)

    fun extract(request: HttpRequest, headerAllowlist: Set<String>, maxFields: Int = 20): List<InjectionPoint> {
        val points = mutableListOf<InjectionPoint>()

        request.parameters().filter { it.type().name == "URL" }.forEach { param ->
            points.add(InjectionPoint(InjectionType.URL_PARAM, param.name(), param.value()))
        }

        request.parameters().filter { it.type().name == "BODY" }.forEach { param ->
            points.add(InjectionPoint(InjectionType.BODY_PARAM, param.name(), param.value()))
        }

        request.parameters().filter { it.type().name == "COOKIE" }.forEach { param ->
            points.add(InjectionPoint(InjectionType.COOKIE, param.name(), param.value()))
        }

        request.headers().forEach { header ->
            if (headerAllowlist.contains(header.name().lowercase())) {
                points.add(InjectionPoint(InjectionType.HEADER, header.name(), header.value()))
            }
        }

        val body = try { request.bodyToString() } catch (_: Exception) { "" }
        val contentType = request.headerValue("Content-Type")?.lowercase() ?: ""
        if (body.isNotBlank()) {
            if (contentType.contains("json") || body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
                points.addAll(extractJsonFields(body, maxFields))
            }
            if (contentType.contains("xml") || body.trimStart().startsWith("<")) {
                points.addAll(extractXmlElements(body, maxFields))
            }
        }

        val path = try { URI(request.url()).path ?: "" } catch (_: Exception) { "" }
        pathIdPattern.findAll(path).forEach { match ->
            points.add(
                InjectionPoint(
                    type = InjectionType.PATH_SEGMENT,
                    name = "path_id",
                    originalValue = match.value,
                    position = match.range.first
                )
            )
        }

        return points
    }

    private fun extractJsonFields(body: String, maxFields: Int): List<InjectionPoint> {
        val results = mutableListOf<InjectionPoint>()
        for (match in jsonFieldPattern.findAll(body)) {
            val name = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            results.add(InjectionPoint(InjectionType.JSON_FIELD, name, value))
            if (results.size >= maxFields) break
        }
        return results
    }

    private fun extractXmlElements(body: String, maxFields: Int): List<InjectionPoint> {
        val results = mutableListOf<InjectionPoint>()
        for (match in xmlElementPattern.findAll(body)) {
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            results.add(InjectionPoint(InjectionType.XML_ELEMENT, name, value))
            if (results.size >= maxFields) break
        }
        return results
    }
}


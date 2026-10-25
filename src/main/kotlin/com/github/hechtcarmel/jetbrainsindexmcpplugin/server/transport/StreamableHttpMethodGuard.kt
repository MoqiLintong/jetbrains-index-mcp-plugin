package com.github.hechtcarmel.jetbrainsindexmcpplugin.server.transport

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText

/**
 * Answers `GET`/`DELETE` on the stateless Streamable HTTP endpoint with a 405 that content
 * negotiation cannot rewrite.
 *
 * kotlin-sdk 0.10.0 registers both methods as `call.reject(405, …)`, and its `reject` sets the
 * status and then `respond(…)`s a `JSONRPCError`. Turning that object into an `OutgoingContent`
 * is content negotiation's job, and it can only do so through a converter whose media type the
 * request's `Accept` header allows. The spec *requires* `text/event-stream` on the optional
 * standalone GET stream and only *allows* `application/json` to be listed as well, so a client
 * that sends `Accept: text/event-stream` alone leaves no usable converter; Ktor's post-render
 * body check then replaces the 405 with an empty `406 Not Acceptable`.
 *
 * The difference is not cosmetic: MCP clients read GET 405 as "this server offers no standalone
 * stream" and continue, while a 406 is a hard transport error that fails the whole connection
 * even though `POST` works. The answer therefore has to be produced before routing, with a body
 * type that skips content negotiation entirely.
 *
 * Remove this once the SDK is bumped past 0.10.0: 0.11.0 serialises these rejections with
 * `respondText`, which is already immune to `Accept`.
 */
internal fun Application.installStreamableHttpMethodGuard(path: String) {
    intercept(ApplicationCallPipeline.Plugins) {
        // Trimmed so a trailing-slash spelling gets the same answer as the SDK route would.
        if (context.request.path().trimEnd('/') != path) return@intercept

        val method = context.request.httpMethod
        if (method != HttpMethod.Get && method != HttpMethod.Delete) return@intercept

        context.response.header(HttpHeaders.Allow, HttpMethod.Post.value)
        context.respondText(
            "Method not allowed.",
            ContentType.Text.Plain,
            HttpStatusCode.MethodNotAllowed,
        )
        finish()
    }
}

package io.github.spasarnaudov.trackertavex.core.network

import io.github.spasarnaudov.trackertavex.core.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    private val validationBody = """
        {"error":{"code":"validation_failed","message":"The portfolio payload is invalid.","details":["holdings[0] has an invalid asset or quantity."]}}
    """.trimIndent()

    @Test
    fun `400 maps to BadRequest`() {
        val error = parseHttpError(400, """{"error":{"code":"bad_request","message":"Malformed JSON."}}""")
        assertTrue(error is AppError.BadRequest)
        assertEquals("Malformed JSON.", error.message)
    }

    @Test
    fun `401 maps to Unauthorized`() {
        val error = parseHttpError(401, """{"error":{"code":"invalid_token","message":"Token expired."}}""")
        assertTrue(error is AppError.Unauthorized)
        assertEquals("Token expired.", error.message)
    }

    @Test
    fun `403 maps to Forbidden`() {
        val error = parseHttpError(403, """{"error":{"code":"forbidden","message":"Insufficient role."}}""")
        assertTrue(error is AppError.Forbidden)
    }

    @Test
    fun `404 maps to NotFound`() {
        val error = parseHttpError(404, """{"error":{"code":"not_found","message":"No such asset."}}""")
        assertTrue(error is AppError.NotFound)
    }

    @Test
    fun `409 with active_session code maps to ActiveSessionConflict`() {
        val error = parseHttpError(409, """{"error":{"code":"active_session","message":"Already logged in."}}""")
        assertTrue(error is AppError.ActiveSessionConflict)
    }

    @Test
    fun `409 with a different code maps to plain Conflict`() {
        val error = parseHttpError(409, """{"error":{"code":"account_locked","message":"Account locked."}}""")
        assertTrue(error is AppError.Conflict)
    }

    @Test
    fun `422 maps to ValidationFailed and keeps details`() {
        val error = parseHttpError(422, validationBody)
        assertTrue(error is AppError.ValidationFailed)
        val validation = error as AppError.ValidationFailed
        assertEquals(1, validation.details.size)
        assertEquals("holdings[0] has an invalid asset or quantity.", validation.details.first())
    }

    @Test
    fun `500 maps to ServerError`() {
        val error = parseHttpError(500, """{"error":{"code":"internal","message":"Boom."}}""")
        assertTrue(error is AppError.ServerError)
    }

    @Test
    fun `unrecognized status maps to Unknown`() {
        val error = parseHttpError(451, """{"error":{"code":"legal","message":"Blocked."}}""")
        assertTrue(error is AppError.Unknown)
        assertEquals("legal", (error as AppError.Unknown).code)
    }

    @Test
    fun `missing or malformed body still produces an error without throwing`() {
        val error = parseHttpError(500, null)
        assertTrue(error is AppError.ServerError)
        assertNull(error.message)
    }

    @Test
    fun `non-JSON body does not crash parsing`() {
        val error = parseHttpError(500, "<html>not json</html>")
        assertTrue(error is AppError.ServerError)
        assertNull(error.message)
    }
}

package io.github.spasarnaudov.trackertavex.core.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionUrlValidatorTest {

    @Test
    fun `blank url is rejected`() {
        assertNotNull(ConnectionUrlValidator.validate(""))
        assertNotNull(ConnectionUrlValidator.validate("   "))
    }

    @Test
    fun `url without a scheme is rejected`() {
        assertNotNull(ConnectionUrlValidator.validate("192.168.1.50:5000/api/v1/"))
    }

    @Test
    fun `ftp scheme is rejected`() {
        assertNotNull(ConnectionUrlValidator.validate("ftp://192.168.1.50/api/v1/"))
    }

    @Test
    fun `valid http url is accepted`() {
        assertNull(ConnectionUrlValidator.validate("http://192.168.1.50:5000/api/v1/"))
    }

    @Test
    fun `valid https url is accepted`() {
        assertNull(ConnectionUrlValidator.validate("https://api.example.com/api/v1/"))
    }

    @Test
    fun `malformed url is rejected`() {
        assertNotNull(ConnectionUrlValidator.validate("http://"))
    }
}

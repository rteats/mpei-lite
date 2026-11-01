package com.rteats.mpeineo.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaNativeLoginFormTest {
    private val loginPage =
        "https://mail.mpei.ru/CookieAuth.dll?GetLogon%3Fcurl=Z2".toHttpUrl()

    @Test fun extractsLiveFieldsRatherThanAssumingFixedParameterNames() {
        val html = """
            <form method="post" action="/CookieAuth.dll">
                <input type="hidden" name="curl" value="Z2">
                <input type="hidden" name="flags" value="4">
                <input type="hidden" name="forcedownlevel" value="0">
                <input type="hidden" name="formdir" value="1">
                <input type="text" name="username">
                <input type="password" name="password">
                <input type="hidden" name="isUtf8" value="1">
                <input type="radio" name="trusted" value="4" checked>
                <input type="radio" name="trusted" value="0">
                <input type="checkbox" name="chpwd" value="1">
                <input type="submit" name="submit" value="Log in">
            </form>
        """.trimIndent()
        val parsed = parseOwaLoginForm(html, loginPage)!!
        assertEquals("/CookieAuth.dll", parsed.action.encodedPath)
        assertEquals("username", parsed.usernameField)
        assertEquals("password", parsed.passwordField)
        assertEquals(
            listOf(
                "curl" to "Z2", "flags" to "4",
                "forcedownlevel" to "0", "formdir" to "1",
                "isUtf8" to "1", "trusted" to "4",
            ),
            parsed.fields,
        )
        val body = parsed.body("test_user", "example_password")
        assertEquals(8, body.size)
        assertTrue((0 until body.size).any { body.name(it) == "username" &&
            body.value(it) == "test_user" })
        assertTrue((0 until body.size).any { body.name(it) == "password" &&
            body.value(it) == "example_password" })
        assertFalse((0 until body.size).any { body.name(it) == "submit" })
    }

    @Test fun preventsPostingCredentialsToAnotherHostOrScheme() {
        val html = """
            <form method="post" action="https://attacker.invalid/collect">
                <input name="username" type="text">
                <input name="password" type="password">
            </form>
        """.trimIndent()
        assertNull(parseOwaLoginForm(html, loginPage))
        assertNull(parseOwaLoginForm(
            html.replace("https://attacker.invalid/collect", "http://mail.mpei.ru/CookieAuth.dll"),
            loginPage,
        ))
    }

    @Test fun refusesGetFormsAndFormsWithoutPassword() {
        val form = """
            <form method="get" action="/CookieAuth.dll">
                <input name="username" type="text">
                <input name="password" type="password">
            </form>
        """.trimIndent()
        assertNull(parseOwaLoginForm(form, loginPage))
        assertNull(parseOwaLoginForm(
            form.replace("type=\"password\"", "type=\"hidden\"")
                .replace("method=\"get\"", "method=\"post\""),
            loginPage,
        ))
    }

    @Test fun excludesUncheckedBoxesAndDisabledFields() {
        val html = """
            <form method="post" action="/CookieAuth.dll">
                <input name="user" type="text">
                <input name="password" type="password">
                <input name="remember" type="checkbox" value="yes">
                <input name="unused" type="hidden" value="ignored" disabled>
                <input name="token" type="hidden" value="csrf">
            </form>
        """.trimIndent()
        assertEquals(listOf("token" to "csrf"),
            parseOwaLoginForm(html, loginPage)!!.fields)
    }
    @Test fun cookieAuthRedirectsAndMailboxPathsAreTrusted() {
        assertTrue(isTrustedOwaAuthUrl(loginPage))
        assertTrue(isTrustedOwaAuthUrl("https://mail.mpei.ru/CookieAuth.dll".toHttpUrl()))
        assertTrue(isTrustedOwaAuthUrl("https://mail.mpei.ru/owa/".toHttpUrl()))
        assertTrue(isTrustedOwaAuthUrl("https://mail.mpei.ru/owa/sessiondata.ashx".toHttpUrl()))
        assertFalse(isTrustedOwaAuthUrl("https://mail.mpei.ru/owa.evil/".toHttpUrl()))
        assertFalse(isTrustedOwaAuthUrl("https://mail.mpei.ru/CookieAuth.dll.evil".toHttpUrl()))
        assertFalse(isTrustedOwaAuthUrl("https://mail.mpei.ru.evil.test/CookieAuth.dll".toHttpUrl()))
        assertFalse(isTrustedOwaAuthUrl("http://mail.mpei.ru/CookieAuth.dll".toHttpUrl()))
    }

}

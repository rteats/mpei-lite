package com.rteats.mpeineo.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaNativeLoginFormTest {
    private val loginPage =
        "https://mail.mpei.ru/owa/auth/logon.aspx?url=%2Fowa%2F".toHttpUrl()

    @Test fun extractsLiveFieldsRatherThanAssumingFixedParameterNames() {
        val html = """
            <form method="post" action="/owa/auth.owa">
                <input type="hidden" name="destination" value="https://mail.mpei.ru/owa/">
                <input type="hidden" name="__RequestVerificationToken" value="dynamic_token">
                <input type="hidden" name="flags" value="4">
                <input type="text" name="customAccount">
                <input type="password" name="customPassword">
                <input type="checkbox" name="remember" value="yes" checked>
                <input type="submit" name="submit" value="Log in">
            </form>
        """.trimIndent()
        val parsed = parseOwaLoginForm(html, loginPage)!!
        assertEquals("/owa/auth.owa", parsed.action.encodedPath)
        assertEquals("customAccount", parsed.usernameField)
        assertEquals("customPassword", parsed.passwordField)
        assertEquals(
            listOf(
                "destination" to "https://mail.mpei.ru/owa/",
                "__RequestVerificationToken" to "dynamic_token",
                "flags" to "4", "remember" to "yes",
            ),
            parsed.fields,
        )
        val body = parsed.body("test_user", "example_password")
        assertEquals(6, body.size)
        assertTrue((0 until body.size).any { body.name(it) == "customAccount" &&
            body.value(it) == "test_user" })
        assertTrue((0 until body.size).any { body.name(it) == "customPassword" &&
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
            html.replace("https://attacker.invalid/collect", "http://mail.mpei.ru/owa/auth.owa"),
            loginPage,
        ))
    }

    @Test fun refusesGetFormsAndFormsWithoutPassword() {
        val form = """
            <form method="get" action="/owa/auth.owa">
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
            <form method="post" action="/owa/auth.owa">
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
}

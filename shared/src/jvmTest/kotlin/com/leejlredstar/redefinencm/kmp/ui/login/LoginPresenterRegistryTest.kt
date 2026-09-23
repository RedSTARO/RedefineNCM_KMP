package com.leejlredstar.redefinencm.kmp.ui.login

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QQPhoneCodeLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QQQrLoginMethod
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The login page draws sections in presenter order, one per shape the provider has methods for,
 * and never draws a method that has no presenter.
 */
class LoginPresenterRegistryTest {
    /** The expected copy below is the Chinese, so the test pins it whatever the machine's language. */
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    private val api = QQMusicApi(HttpClient(MockEngine { respond("") }), baseUrl = { "" })
    private val qq = QQQrLoginMethod(api, QQQrLoginMethod.Kind.QQ)
    private val wechat = QQQrLoginMethod(api, QQQrLoginMethod.Kind.WECHAT)
    private val phone = QQPhoneCodeLoginMethod(api)
    private val text = QQCredentialTextLoginMethod()

    @Test
    fun sectionsFollowPresenterOrderAndGroupOneShapeTogether() {
        val registry = LoginPresenterRegistry(
            listOf(QrLoginPresenter(), PhoneCodeLoginPresenter(), CredentialTextLoginPresenter()),
        )

        // Registration order of the methods does not decide the page; the presenters do.
        val sections = registry.sections(listOf(text, wechat, phone, qq))

        assertEquals(listOf("扫码登录", "手机验证码", "手动输入"), sections.map { it.title })
        assertEquals(listOf("qq.qr.wx", "qq.qr.qq"), sections[0].methods.map { it.id })
        assertEquals(listOf("qq.phone"), sections[1].methods.map { it.id })
        assertEquals("QQ 扫码", QrLoginPresenter().sectionTitle(listOf(qq)))
    }

    @Test
    fun aMethodNoPresenterSupportsIsLeftOut() {
        val registry = LoginPresenterRegistry(listOf(CredentialTextLoginPresenter()))

        val sections = registry.sections(listOf(qq, text, phone))

        assertEquals(1, sections.size)
        assertEquals(listOf("qq.credential"), sections.single().methods.map { it.id })
        assertNull(registry.presenterFor(phone))
    }
}

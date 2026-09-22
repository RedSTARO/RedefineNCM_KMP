package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two shared state machines, driven against scripted methods and a fake host. A presenter is
 * only a view over these, so this is where the login page's behaviour is pinned down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginFlowsTest {
    private class FakeHost(
        override val scope: CoroutineScope,
        private val persistOutcome: (String) -> Result<Unit> = { Result.success(Unit) },
    ) : LoginHost {
        override val provider = MusicProviderId.QQ
        override val storedCredential = MutableStateFlow("")
        override val signedIn = MutableStateFlow(false)
        val persisted = mutableListOf<String>()
        var signedInCalls = 0

        override suspend fun persist(credential: String): Result<Unit> {
            val outcome = persistOutcome(credential)
            if (outcome.isSuccess) {
                persisted += credential
                storedCredential.value = credential
                signedIn.value = credential.isNotBlank()
            }
            return outcome
        }

        override fun onSignedIn() {
            signedInCalls += 1
        }
    }

    private class ScriptedQr(
        answers: List<QrLoginPoll>,
        private val startFailure: String? = null,
        override val lifetimeMillis: Long = 60_000L,
    ) : QrLoginMethod {
        override val id = "test.qr"
        override val provider = MusicProviderId.QQ
        override val displayName = "扫码"
        override val scanHint = "scan me"
        override val pollIntervalMillis = 1_000L
        private val answers = ArrayDeque(answers)
        var polls = 0

        override suspend fun start(): QrLoginSession {
            startFailure?.let { throw LoginMethodException(it) }
            return QrLoginSession("t", byteArrayOf(1, 2, 3))
        }

        override suspend fun poll(session: QrLoginSession): QrLoginPoll {
            polls += 1
            return answers.removeFirstOrNull() ?: QrLoginPoll.Waiting()
        }
    }

    private fun TestScope.qrFlow(method: QrLoginMethod, host: FakeHost = FakeHost(this)) =
        QrLoginFlow(method, host, networkDispatcher = StandardTestDispatcher(testScheduler)) to host

    @Test
    fun qrFlowShowsTheCodeThenPersistsOnConfirmation() = runTest {
        val method = ScriptedQr(
            listOf(QrLoginPoll.Waiting(), QrLoginPoll.Scanned, QrLoginPoll.Confirmed("musicid=1; musickey=k")),
        )
        val (flow, host) = qrFlow(method)

        flow.start()
        runCurrent()
        assertContentEquals(byteArrayOf(1, 2, 3), flow.imagePng.value)
        assertFalse(flow.loading.value)
        assertEquals("scan me", flow.status.value)

        advanceTimeBy(2_100)
        runCurrent()
        assertEquals("请在手机上确认登录", flow.status.value)

        advanceUntilIdle()
        assertEquals(listOf("musicid=1; musickey=k"), host.persisted)
        assertTrue(flow.success.value)
        assertEquals(1, host.signedInCalls)
        assertEquals("登录成功！", flow.status.value)
        assertEquals(3, method.polls)
    }

    @Test
    fun qrFlowEndsOnExpiredRefusedAndFailedWithoutPersisting() = runTest {
        val expired = qrFlow(ScriptedQr(listOf(QrLoginPoll.Expired)))
        expired.first.start()
        advanceUntilIdle()
        assertTrue(expired.first.expired.value)
        assertEquals("二维码已过期，请重新生成", expired.first.status.value)

        val refused = qrFlow(ScriptedQr(listOf(QrLoginPoll.Refused)))
        refused.first.start()
        advanceUntilIdle()
        assertTrue(refused.first.expired.value)

        val failed = qrFlow(ScriptedQr(listOf(QrLoginPoll.Failed("boom"))))
        failed.first.start()
        advanceUntilIdle()
        assertEquals("boom", failed.first.error.value)
        assertFalse(failed.first.success.value)

        assertTrue(expired.second.persisted.isEmpty())
        assertTrue(refused.second.persisted.isEmpty())
        assertTrue(failed.second.persisted.isEmpty())
    }

    @Test
    fun qrFlowReportsAMethodThatCannotIssueACode() = runTest {
        val (flow, _) = qrFlow(ScriptedQr(emptyList(), startFailure = "nope"))
        flow.start()
        advanceUntilIdle()
        assertEquals("生成二维码失败", flow.status.value)
        assertEquals("nope", flow.error.value)
        assertFalse(flow.loading.value)
        assertNull(flow.imagePng.value)
    }

    @Test
    fun qrFlowExpiresTheCodeOnItsOwnAfterItsLifetime() = runTest {
        val method = ScriptedQr(emptyList(), lifetimeMillis = 3_500)
        val (flow, host) = qrFlow(method)
        flow.start()
        advanceUntilIdle()
        assertTrue(flow.expired.value)
        assertEquals(3, method.polls)
        assertTrue(host.persisted.isEmpty())
    }

    @Test
    fun qrFlowShowsAPersistFailureAndDoesNotFinishThePage() = runTest {
        val host = FakeHost(this) { Result.failure(IllegalStateException("disk")) }
        val (flow, _) = qrFlow(ScriptedQr(listOf(QrLoginPoll.Confirmed("c"))), host)
        flow.start()
        advanceUntilIdle()
        assertEquals("登录状态保存失败", flow.status.value)
        assertEquals("disk", flow.error.value)
        assertEquals(0, host.signedInCalls)
        assertFalse(flow.success.value)
    }

    @Test
    fun qrFlowCancelResetsAndStopsPolling() = runTest {
        val method = ScriptedQr(listOf(QrLoginPoll.Waiting(), QrLoginPoll.Confirmed("c")))
        val (flow, host) = qrFlow(method)
        flow.start()
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(1, method.polls)

        flow.cancel()
        assertNull(flow.imagePng.value)
        assertEquals("点击生成二维码", flow.status.value)
        assertFalse(flow.loading.value)

        advanceUntilIdle()
        assertEquals(1, method.polls)
        assertTrue(host.persisted.isEmpty())
    }

    private class ScriptedPhone(
        private val onSend: (String) -> PhoneCodeSend,
        private val onVerify: (String, String) -> PhoneCodeVerify = { _, _ -> PhoneCodeVerify.Confirmed("cred") },
    ) : PhoneCodeLoginMethod {
        override val id = "test.phone"
        override val provider = MusicProviderId.QQ
        override val displayName = "手机"
        override val phoneHint = "hint"
        override val resendIntervalSeconds = 3
        val sent = mutableListOf<String>()

        override suspend fun sendCode(phone: String): PhoneCodeSend {
            sent += phone
            return onSend(phone)
        }

        override suspend fun verify(phone: String, code: String): PhoneCodeVerify = onVerify(phone, code)
    }

    private fun TestScope.phoneFlow(method: PhoneCodeLoginMethod, host: FakeHost = FakeHost(this)) =
        PhoneCodeLoginFlow(method, host, networkDispatcher = StandardTestDispatcher(testScheduler)) to host

    @Test
    fun phoneFlowRejectsAShortNumberWithoutAskingTheProvider() = runTest {
        val method = ScriptedPhone({ PhoneCodeSend.Sent })
        val (flow, _) = phoneFlow(method)
        flow.setPhone("123")
        flow.sendCode()
        advanceUntilIdle()
        assertEquals("请输入正确的手机号", flow.error.value)
        assertTrue(method.sent.isEmpty())
    }

    @Test
    fun phoneFlowSendsDigitsOnlyThenCountsDownBeforeAllowingAResend() = runTest {
        val method = ScriptedPhone({ PhoneCodeSend.Sent })
        val (flow, _) = phoneFlow(method)
        flow.setPhone("138 0013 8000")
        flow.sendCode()
        runCurrent()
        assertEquals(listOf("13800138000"), method.sent)
        assertTrue(flow.codeSent.value)
        assertTrue(flow.message.value.contains("13800138000"))
        assertEquals(3, flow.resendInSeconds.value)
        assertFalse(flow.canSendCode)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, flow.resendInSeconds.value)

        advanceUntilIdle()
        assertEquals(0, flow.resendInSeconds.value)
        assertTrue(flow.canSendCode)
    }

    @Test
    fun phoneFlowKeepsTheCodeFieldClosedWhenTheProviderWantsACaptcha() = runTest {
        val (flow, _) = phoneFlow(ScriptedPhone({ PhoneCodeSend.Blocked("captcha first") }))
        flow.setPhone("13800138000")
        flow.sendCode()
        advanceUntilIdle()
        assertFalse(flow.codeSent.value)
        assertEquals("captcha first", flow.message.value)
        assertEquals(0, flow.resendInSeconds.value)
        assertFalse(flow.canVerify)
    }

    @Test
    fun phoneFlowShowsASendFailure() = runTest {
        val (flow, _) = phoneFlow(ScriptedPhone({ PhoneCodeSend.Failed("too many") }))
        flow.setPhone("13800138000")
        flow.sendCode()
        advanceUntilIdle()
        assertEquals("too many", flow.error.value)
        assertFalse(flow.codeSent.value)
    }

    @Test
    fun phoneFlowVerifiesTheTypedCodeAndPersists() = runTest {
        val method = ScriptedPhone(
            onSend = { PhoneCodeSend.Sent },
            onVerify = { phone, code -> if (code == "123456") PhoneCodeVerify.Confirmed("cred:$phone") else PhoneCodeVerify.Failed("wrong") },
        )
        val (flow, host) = phoneFlow(method)
        flow.setPhone("13800138000")
        flow.sendCode()
        runCurrent()

        flow.setCode(" 000000 ")
        flow.verify()
        runCurrent()
        assertEquals("wrong", flow.error.value)
        assertTrue(host.persisted.isEmpty())

        flow.setCode("123456")
        flow.verify()
        advanceUntilIdle()
        assertEquals(listOf("cred:13800138000"), host.persisted)
        assertTrue(flow.success.value)
        assertEquals(1, host.signedInCalls)
        assertFalse(flow.canVerify)
    }
}

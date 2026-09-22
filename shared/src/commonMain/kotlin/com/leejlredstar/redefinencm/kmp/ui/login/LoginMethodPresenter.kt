package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/**
 * The UI half of a login source: draws every [LoginMethod] of one shape and owns its on-screen
 * state. The login page never inspects a method's shape itself; it asks [LoginPresenterRegistry]
 * which presenter takes it.
 *
 * A presenter reaches persistence and navigation only through the [LoginHost] it is handed, so a
 * new shape of login is a presenter plus its method interface and nothing in the page.
 */
interface LoginMethodPresenter {
    /** Whether this presenter draws [method]. */
    fun supports(method: LoginMethod): Boolean

    /**
     * The title of the section [methods] share. Methods drawn by the same presenter for one
     * provider sit in one section with a chooser, so two scan apps are one "扫码登录" card.
     */
    fun sectionTitle(methods: List<LoginMethod>): String

    /** Draws [method]. Called only for methods [supports] accepted. */
    @Composable
    fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette)
}

/** One card on the login page: a presenter and the methods it draws there. */
class LoginSection(
    val presenter: LoginMethodPresenter,
    val methods: List<LoginMethod>,
) {
    val title: String get() = presenter.sectionTitle(methods)
}

/** The registered presenters, in the order their sections appear on the page. */
class LoginPresenterRegistry(private val presenters: List<LoginMethodPresenter>) {
    val all: List<LoginMethodPresenter> get() = presenters

    fun presenterFor(method: LoginMethod): LoginMethodPresenter? =
        presenters.firstOrNull { it.supports(method) }

    /**
     * Groups [methods] into sections by the presenter that draws them, in presenter order. A
     * method no presenter supports is left out: it is a logic half without a UI half, which is a
     * registration mistake rather than something to draw a placeholder for.
     */
    fun sections(methods: List<LoginMethod>): List<LoginSection> =
        presenters.mapNotNull { presenter ->
            methods.filter(presenter::supports)
                .takeIf { it.isNotEmpty() }
                ?.let { LoginSection(presenter, it) }
        }
}

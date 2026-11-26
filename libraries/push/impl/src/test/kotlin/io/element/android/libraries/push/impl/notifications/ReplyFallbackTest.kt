/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyFallbackTest {
    @Test
    fun `the quote of a plain reply is left out`() {
        assertThat(withoutReplyFallback("> <@paula:ferrao.org> vens jantar?\n> e a Ema?\n\nsim, às 8\nlevo vinho"))
            .isEqualTo("sim, às 8\nlevo vinho")
    }

    @Test
    fun `a message without a reply fallback stays as it is`() {
        assertThat(withoutReplyFallback("olá")).isEqualTo("olá")
        assertThat(withoutReplyFallback("> uma citação minha\n\ntexto")).isEqualTo("> uma citação minha\n\ntexto")
        assertThat(withoutReplyFallback("> <@a:b> só a citação")).isEqualTo("> <@a:b> só a citação")
    }
}

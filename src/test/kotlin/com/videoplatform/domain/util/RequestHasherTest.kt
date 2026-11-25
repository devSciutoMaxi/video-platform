package com.videoplatform.domain.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestHasherTest {

    @Test
    fun `mismos campos producen el mismo hash`() {
        assertThat(RequestHasher.hash("a", "b", null)).isEqualTo(RequestHasher.hash("a", "b", null))
    }

    @Test
    fun `campos distintos producen hashes distintos`() {
        assertThat(RequestHasher.hash("a", "b")).isNotEqualTo(RequestHasher.hash("a", "c"))
    }

    @Test
    fun `el separador evita colisiones entre campos adyacentes`() {
        assertThat(RequestHasher.hash("ab", "c")).isNotEqualTo(RequestHasher.hash("a", "bc"))
    }

    @Test
    fun `el hash es SHA-256 en hexadecimal`() {
        assertThat(RequestHasher.hash("x")).hasSize(64).matches("[0-9a-f]{64}")
    }
}

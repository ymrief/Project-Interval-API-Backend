package com.interval.apibackend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenUtilsTest {

    @Test
    void sha256Hex_matchesKnownVector() {
        assertThat(TokenUtils.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_isAlways64Characters() {
        assertThat(TokenUtils.sha256Hex("apa saja")).hasSize(64);
    }

    @Test
    void generatedRefreshTokens_areUniqueAndUrlSafe() {
        String first = TokenUtils.generateRefreshToken();
        String second = TokenUtils.generateRefreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
    }
}

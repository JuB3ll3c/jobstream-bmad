package com.jobstream.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RestClientConfigTest {

	@Test
	void givenConfig_thenHttpClientSettingsApplyConnectAndReadTimeouts() {
		assertThat(RestClientConfig.httpClientSettings().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(RestClientConfig.httpClientSettings().readTimeout()).isEqualTo(Duration.ofSeconds(30));
	}
}

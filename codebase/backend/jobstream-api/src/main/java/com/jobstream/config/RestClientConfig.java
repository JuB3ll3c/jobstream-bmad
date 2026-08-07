package com.jobstream.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client infrastructure for outbound calls. Sync {@link RestClient}
 * (Spring 6.1+) is used because jobstream-api is a blocking WebMVC service —
 * no reactive stack. Default timeouts.
 *
 * <p>Variance from architecture spine: spine lists {@code WebClientConfig};
 * implemented as {@code RestClientConfig} in the same {@code config} package.
 */
@Configuration
public class RestClientConfig {

	@Bean
	public RestClient restClient(RestClient.Builder builder) {
		return builder.build();
	}

	@Bean
	public RestClientCustomizer restClientCustomizer() {
		return builder -> builder.requestFactory(
				ClientHttpRequestFactoryBuilder.jdk().build(httpClientSettings()));
	}

	static HttpClientSettings httpClientSettings() {
		return HttpClientSettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(5))
				.withReadTimeout(Duration.ofSeconds(30));
	}
}
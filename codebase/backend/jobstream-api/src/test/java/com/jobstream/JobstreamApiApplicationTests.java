package com.jobstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jobstream.adapter.out.jobprovider.AdzunaAdapter;
import com.jobstream.adapter.out.jobprovider.JSearchAdapter;
import com.jobstream.port.JobProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
		"JSEARCH_API_KEY=test-key",
		"ADZUNA_APP_ID=test-id",
		"ADZUNA_APP_SECRET=test-secret"
})
class JobstreamApiApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

	@Autowired
	private List<JobProvider> jobProviders;

	@Test
	void contextLoads() {
	}

	@Test
	void givenSpringContext_whenJobProviderBeansInjected_thenBothAdaptersAreRegistered() {
		assertThat(jobProviders)
				.hasAtLeastOneElementOfType(JSearchAdapter.class)
				.hasAtLeastOneElementOfType(AdzunaAdapter.class);
	}

}

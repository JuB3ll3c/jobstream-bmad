package com.jobstream.adapter.out.jobprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.jobstream.domain.JobOffer;
import com.jobstream.domain.JobProviderException;
import com.jobstream.domain.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AdzunaAdapterTest {

	private static final String URI =
			"https://api.adzuna.com/v1/api/jobs/us/search/1?app_id=test-id&app_key=test-secret&what=java&content-type=application/json";

	private MockRestServiceServer server;
	private AdzunaAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new AdzunaAdapter(builder.build(), "https://api.adzuna.com", "test-id", "test-secret", "us");
	}

	@Test
	void givenResultsResponse_whenSearch_thenOffersMapProviderAndExternalId() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess(resultsJson(), MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).hasSize(2);
		JobOffer first = offers.get(0);
		assertThat(first.provider()).isEqualTo(Provider.ADZUNA);
		assertThat(first.externalId()).isEqualTo("39502843");
		assertThat(first.title()).isEqualTo("Senior Java Engineer");
		assertThat(first.company()).isEqualTo("Acme Inc");
		assertThat(first.url()).isEqualTo("https://adzuna.example/redirect/39502843");
		assertThat(first.id()).isNotNull();

		JobOffer second = offers.get(1);
		assertThat(second.provider()).isEqualTo(Provider.ADZUNA);
		assertThat(second.externalId()).isEqualTo("39502844");
		server.verify();
	}

	@Test
	void givenHtmlDescription_whenSearch_thenTagsAndEntitiesAreStripped() {
		String json = """
				{ "results": [
					{ "title": "Backend Engineer", "company": { "display_name": "Globex" },
					  "description": "<b>Java</b> &amp; <i>Spring</i> &#39;cloud&#39; &ndash; cool",
					  "redirect_url": "https://adzuna.example/redirect/1", "id": 1 }
				] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).singleElement().satisfies(offer ->
				assertThat(offer.description()).isEqualTo("Java & Spring 'cloud' – cool"));
		server.verify();
	}

	@Test
	void givenMalformedNumericEntities_whenSearch_thenEntityLeftLiteralWithoutCrash() {
		String json = """
				{ "results": [
					{ "title": "Backend Engineer", "company": { "display_name": "Globex" },
					  "description": "a &#1114112; b &#x1F600; c &#999999999999999999999; d",
					  "redirect_url": "https://adzuna.example/redirect/1", "id": 1 }
				] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).singleElement().satisfies(offer -> assertThat(offer.description())
				.isEqualTo("a &#1114112; b 😀 c &#999999999999999999999; d"));
		server.verify();
	}

	@Test
	void givenHttpError_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> adapter.search("java")).isInstanceOf(JobProviderException.class);
		server.verify();
	}

	@Test
	void givenMalformedPayload_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess("{ not valid json", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.search("java")).isInstanceOf(JobProviderException.class);
		server.verify();
	}

	@Test
	void givenResponseWithoutResults_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess("{ \"count\": 0 }", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.search("java")).isInstanceOf(JobProviderException.class);
		server.verify();
	}

	@Test
	void givenEmptyBody_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess("", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.search("java")).isInstanceOf(JobProviderException.class);
		server.verify();
	}

	@Test
	void givenBlankAppId_whenConstructed_thenThrows() {
		RestClient.Builder builder = RestClient.builder();

		assertThatThrownBy(() -> new AdzunaAdapter(builder.build(), "https://api.adzuna.com", "", "test-secret", "us"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void givenBlankAppSecret_whenConstructed_thenThrows() {
		RestClient.Builder builder = RestClient.builder();

		assertThatThrownBy(() -> new AdzunaAdapter(builder.build(), "https://api.adzuna.com", "test-id", "", "us"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void givenBlankCountry_whenConstructed_thenThrows() {
		RestClient.Builder builder = RestClient.builder();

		assertThatThrownBy(() -> new AdzunaAdapter(builder.build(), "https://api.adzuna.com", "test-id", "test-secret", "  "))
				.isInstanceOf(IllegalStateException.class);
	}

	private static String resultsJson() {
		return """
				{ "count": 2, "results": [
					{ "title": "Senior Java Engineer", "company": { "display_name": "Acme Inc" },
					  "description": "Develop <b>APIs</b> in Java &amp; Spring",
					  "redirect_url": "https://adzuna.example/redirect/39502843",
					  "id": 39502843, "created": "2026-07-29T10:00:00Z" },
					{ "title": "Java Developer", "company": { "display_name": "Globex" },
					  "description": "Build services",
					  "redirect_url": "https://adzuna.example/redirect/39502844",
					  "id": 39502844, "created": "2026-07-28T10:00:00Z" }
				] }""";
	}
}

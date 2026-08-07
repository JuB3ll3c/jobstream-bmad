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

class JSearchAdapterTest {

	private static final String URI =
			"https://serpapi.com/search.json?engine=google_jobs&q=java&api_key=test-key";

	private MockRestServiceServer server;
	private JSearchAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new JSearchAdapter(builder.build(), "https://serpapi.com", "test-key");
	}

	@Test
	void givenJobsResponse_whenSearch_thenOffersMapProviderAndExternalId() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess(jobsJson(), MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).hasSize(2);
		JobOffer first = offers.get(0);
		assertThat(first.provider()).isEqualTo(Provider.JSEARCH);
		assertThat(first.externalId()).isEqualTo("job-1");
		assertThat(first.title()).isEqualTo("Software Engineer");
		assertThat(first.company()).isEqualTo("Acme Inc");
		assertThat(first.url()).isEqualTo("https://acme.com/apply");
		assertThat(first.description()).isEqualTo("Build cool stuff");
		assertThat(first.id()).isNotNull();

		JobOffer second = offers.get(1);
		assertThat(second.provider()).isEqualTo(Provider.JSEARCH);
		assertThat(second.externalId()).isEqualTo("job-2");
		assertThat(second.title()).isEqualTo("Backend Engineer");
		assertThat(second.company()).isEqualTo("Globex");
		assertThat(second.url()).isEqualTo("https://globex.com/apply");
		assertThat(second.description()).isEqualTo("Role");
		server.verify();
	}

	@Test
	void givenJobWithoutApplyOptions_whenSearch_thenUrlFallsBackToVia() {
		String json = """
				{ "jobs_results": [
					{ "title": "Dev", "company_name": "Acme", "job_id": "job-3",
					  "via": "LinkedIn", "description": "Role" }
				] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).singleElement().satisfies(offer -> {
			assertThat(offer.url()).isEqualTo("LinkedIn");
			assertThat(offer.externalId()).isEqualTo("job-3");
		});
		server.verify();
	}

	@Test
	void givenJobWithBlankApplyLink_whenSearch_thenUrlFallsBackToVia() {
		String json = """
				{ "jobs_results": [
					{ "title": "Dev", "company_name": "Acme", "job_id": "job-3",
					  "via": "LinkedIn", "description": "Role",
					  "apply_options": [ { "title": "Apply", "link": "  " } ] }
				] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).singleElement().satisfies(offer -> assertThat(offer.url()).isEqualTo("LinkedIn"));
		server.verify();
	}

	@Test
	void givenJobWithoutDescription_whenSearch_thenDescriptionIsNull() {
		String json = """
				{ "jobs_results": [
					{ "title": "Dev", "company_name": "Acme", "job_id": "job-4",
					  "apply_options": [ { "link": "https://acme.com/apply" } ] }
				] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<JobOffer> offers = adapter.search("java");

		assertThat(offers).singleElement().satisfies(offer -> assertThat(offer.description()).isNull());
		server.verify();
	}

	@Test
	void givenJobMissingExternalId_whenSearch_thenThrows() {
		String json = """
				{ "jobs_results": [ { "title": "Dev", "company_name": "Acme" } ] }""";

		server.expect(once(), requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.search("java")).isInstanceOf(JobProviderException.class);
		server.verify();
	}

	@Test
	void givenHttpError_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

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
	void givenResponseWithoutJobsResults_whenSearch_thenThrowsJobProviderException() {
		server.expect(once(), requestTo(URI))
				.andRespond(withSuccess("{ \"other\": [] }", MediaType.APPLICATION_JSON));

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
	void givenBlankApiKey_whenConstructed_thenThrows() {
		RestClient.Builder builder = RestClient.builder();

		assertThatThrownBy(() -> new JSearchAdapter(builder.build(), "https://serpapi.com", ""))
				.isInstanceOf(IllegalStateException.class);
	}

	private static String jobsJson() {
		return """
				{ "jobs_results": [
					{ "title": "Software Engineer", "company_name": "Acme Inc",
					  "via": "LinkedIn", "job_id": "job-1",
					  "description": "Build cool stuff",
					  "apply_options": [ { "title": "Apply", "link": "https://acme.com/apply" } ] },
					{ "title": "Backend Engineer", "company_name": "Globex",
					  "via": "Indeed", "job_id": "job-2",
					  "description": "Role",
					  "apply_options": [ { "title": "Apply", "link": "https://globex.com/apply" } ] }
				] }""";
	}
}

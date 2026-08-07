package com.jobstream.adapter.out.jobprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import com.jobstream.domain.JobOffer;
import com.jobstream.domain.JobProviderException;
import com.jobstream.domain.Provider;
import com.jobstream.port.JobProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter for the SerpApi Google Jobs engine (JSearch). Maps {@code jobs_results[]}
 * onto normalized {@link JobOffer} instances. Credentials come from the
 * {@code JSEARCH_API_KEY} environment variable only (NFR-3).
 */
@Component
public class JSearchAdapter implements JobProvider {

	private final RestClient restClient;
	private final String apiKey;

	public JSearchAdapter(RestClient restClient,
			@Value("${JSEARCH_BASE_URL:https://serpapi.com}") String baseUrl,
			@Value("${JSEARCH_API_KEY:}") String apiKey) {
		this.restClient = restClient.mutate().baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"JSEARCH_API_KEY environment variable is required but is missing or blank");
		}
	}

	@Override
	public List<JobOffer> search(String query) {
		return mapResults(fetch(query));
	}

	private JsonNode fetch(String query) {
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/search.json")
							.queryParam("engine", "google_jobs")
							.queryParam("q", query)
							.queryParam("api_key", apiKey)
							.build())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw new JobProviderException(
								"JSearch request failed with HTTP status " + response.getStatusCode().value());
					})
					.body(JsonNode.class);
		} catch (RestClientException e) {
			throw new JobProviderException("JSearch request failed", e);
		}
	}

	private List<JobOffer> mapResults(JsonNode root) {
		if (root == null) {
			throw new JobProviderException("JSearch response body is empty");
		}
		JsonNode results = root.get("jobs_results");
		if (results == null || !results.isArray()) {
			throw new JobProviderException("JSearch response is missing the 'jobs_results' array");
		}
		List<JobOffer> offers = new ArrayList<>();
		for (JsonNode item : results) {
			offers.add(toJobOffer(item));
		}
		return offers;
	}

	private JobOffer toJobOffer(JsonNode item) {
		String externalId = textOrNull(item, "job_id");
		if (externalId == null) {
			throw new JobProviderException("JSearch job is missing 'job_id'");
		}
		String url = firstApplyLink(item);
		if (url == null) {
			url = textOrNull(item, "via");
		}
		return new JobOffer(UUID.randomUUID(), externalId, Provider.JSEARCH,
				textOrNull(item, "title"), textOrNull(item, "company_name"), url,
				textOrNull(item, "description"));
	}

	private static String firstApplyLink(JsonNode item) {
		JsonNode options = item.get("apply_options");
		if (options != null && options.isArray() && options.size() > 0) {
			String link = textOrNull(options.get(0), "link");
			if (link != null && !link.isBlank()) {
				return link;
			}
		}
		return null;
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.asText();
	}
}

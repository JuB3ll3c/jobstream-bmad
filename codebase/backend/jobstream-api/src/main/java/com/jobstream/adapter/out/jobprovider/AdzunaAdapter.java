package com.jobstream.adapter.out.jobprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobstream.domain.JobOffer;
import com.jobstream.domain.JobProviderException;
import com.jobstream.domain.Provider;
import com.jobstream.port.JobProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Adapter for the Adzuna Jobs API. Maps {@code results[]} onto normalized
 * {@link JobOffer} instances, stripping HTML from descriptions. Credentials
 * come from the {@code ADZUNA_APP_ID} and {@code ADZUNA_APP_SECRET} environment
 * variables only (NFR-3).
 */
@Component
public class AdzunaAdapter implements JobProvider {

	private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d+|x[0-9a-fA-F]+);");

	private final RestClient restClient;
	private final String appId;
	private final String appKey;
	private final String country;

	public AdzunaAdapter(RestClient restClient,
			@Value("${ADZUNA_BASE_URL:https://api.adzuna.com}") String baseUrl,
			@Value("${ADZUNA_APP_ID:}") String appId,
			@Value("${ADZUNA_APP_SECRET:}") String appKey,
			@Value("${ADZUNA_COUNTRY:ch}") String country) {
		this.restClient = restClient.mutate().baseUrl(baseUrl).build();
		this.appId = appId;
		this.appKey = appKey;
		this.country = country;
		if (appId == null || appId.isBlank()) {
			throw new IllegalStateException(
					"ADZUNA_APP_ID environment variable is required but is missing or blank");
		}
		if (appKey == null || appKey.isBlank()) {
			throw new IllegalStateException(
					"ADZUNA_APP_SECRET environment variable is required but is missing or blank");
		}
		if (country == null || country.isBlank()) {
			throw new IllegalStateException(
					"ADZUNA_COUNTRY environment variable is required but is missing or blank");
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
							.path("/v1/api/jobs/{country}/search/1")
							.queryParam("app_id", appId)
							.queryParam("app_key", appKey)
							.queryParam("what", query)
							.queryParam("content-type", "application/json")
							.build(country))
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw new JobProviderException(
								"Adzuna request failed with HTTP status " + response.getStatusCode().value());
					})
					.body(JsonNode.class);
		} catch (RestClientException e) {
			throw new JobProviderException("Adzuna request failed", e);
		}
	}

	private List<JobOffer> mapResults(JsonNode root) {
		if (root == null) {
			throw new JobProviderException("Adzuna response body is empty");
		}
		JsonNode results = root.get("results");
		if (results == null || !results.isArray()) {
			throw new JobProviderException("Adzuna response is missing the 'results' array");
		}
		List<JobOffer> offers = new ArrayList<>();
		for (JsonNode item : results) {
			offers.add(toJobOffer(item));
		}
		return offers;
	}

	private JobOffer toJobOffer(JsonNode item) {
		String externalId = textOrNull(item, "id");
		if (externalId == null) {
			throw new JobProviderException("Adzuna job is missing 'id'");
		}
		return new JobOffer(UUID.randomUUID(), externalId, Provider.ADZUNA,
				textOrNull(item, "title"), companyDisplayName(item),
				textOrNull(item, "redirect_url"), stripHtml(textOrNull(item, "description")));
	}

	private static String companyDisplayName(JsonNode item) {
		JsonNode company = item.get("company");
		if (company == null || company.isNull()) {
			return null;
		}
		return textOrNull(company, "display_name");
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.asText();
	}

	private static String stripHtml(String raw) {
		if (raw == null) {
			return null;
		}
		return unescapeEntities(raw.replaceAll("<[^>]+>", ""));
	}

	private static String unescapeEntities(String text) {
		String result = text
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&nbsp;", " ")
				.replace("&ndash;", "\u2013")
				.replace("&mdash;", "\u2014")
				.replace("&amp;", "&");
		Matcher matcher = NUMERIC_ENTITY.matcher(result);
		StringBuilder decoded = new StringBuilder();
		while (matcher.find()) {
			String code = matcher.group(1);
			Integer codepoint = decodeNumericEntity(code);
			if (codepoint != null && Character.isValidCodePoint(codepoint)) {
				matcher.appendReplacement(decoded, Matcher.quoteReplacement(new String(Character.toChars(codepoint))));
			} else {
				matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(decoded);
		return decoded.toString();
	}

	private static Integer decodeNumericEntity(String code) {
		try {
			int radix = (code.charAt(0) == 'x' || code.charAt(0) == 'X') ? 16 : 10;
			return Integer.parseInt(radix == 16 ? code.substring(1) : code, radix);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

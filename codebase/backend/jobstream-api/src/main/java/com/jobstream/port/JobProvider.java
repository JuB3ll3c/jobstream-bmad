package com.jobstream.port;

import java.util.List;

import com.jobstream.domain.JobOffer;

/**
 * Port owned by the application layer: the only boundary through which job
 * sources are searched. Framework-free — implementations (adapters) live in
 * {@code adapter/out/jobprovider}.
 */
public interface JobProvider {

	/**
	 * Searches the provider for job offers matching the given query.
	 *
	 * @param query search terms, never null
	 * @return normalized job offers from the provider
	 * @throws com.jobstream.domain.JobProviderException on provider failure
	 */
	List<JobOffer> search(String query);
}

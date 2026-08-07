package com.jobstream.domain;

/**
 * Unchecked exception thrown by a {@code JobProvider} adapter when the provider
 * call fails (HTTP / network / malformed payload). The consuming use case can
 * catch it per provider and degrade gracefully (FR-1).
 */
public class JobProviderException extends RuntimeException {

	public JobProviderException(String message) {
		super(message);
	}

	public JobProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}

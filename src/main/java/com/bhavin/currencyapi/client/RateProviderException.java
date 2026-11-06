package com.bhavin.currencyapi.client;

/** Raised when an external rate provider cannot supply the requested rates. */
public class RateProviderException extends RuntimeException {

    public enum Kind {
        /** Provider rejected our request (HTTP 4xx), e.g. unknown currency. */
        CLIENT_ERROR,
        /** Provider failed on its side (HTTP 5xx). */
        SERVER_ERROR,
        /** Connection failed or timed out. */
        UNREACHABLE,
        /** Provider answered, but the body was missing or unreadable. */
        BAD_RESPONSE
    }

    private final Kind kind;

    public RateProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public RateProviderException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind getKind() {
        return kind;
    }
}

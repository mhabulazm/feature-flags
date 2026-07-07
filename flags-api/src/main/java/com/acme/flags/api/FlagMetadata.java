package com.acme.flags.api;

import java.time.Duration;
import java.util.Objects;

public record FlagMetadata(
        String owner,
        String ticket,
        boolean defaultValue,
        FailureSemantics failureSemantics,
        Duration expiresAfter
) {
    public FlagMetadata {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(failureSemantics, "failureSemantics must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String owner;
        private String ticket;
        private boolean defaultValue;
        private FailureSemantics failureSemantics;
        private Duration expiresAfter;

        private Builder() {
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder ticket(String ticket) {
            this.ticket = ticket;
            return this;
        }

        public Builder defaultValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder failureSemantics(FailureSemantics failureSemantics) {
            this.failureSemantics = failureSemantics;
            return this;
        }

        public Builder expiresAfter(Duration expiresAfter) {
            this.expiresAfter = expiresAfter;
            return this;
        }

        public FlagMetadata build() {
            return new FlagMetadata(owner, ticket, defaultValue, failureSemantics, expiresAfter);
        }
    }
}

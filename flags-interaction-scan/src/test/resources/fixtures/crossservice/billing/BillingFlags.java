package fixtures.billing;

public enum BillingFlags implements FlagKey {
    RATE_LIMIT("billing.rate-limit-override");

    private final String key;

    BillingFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}

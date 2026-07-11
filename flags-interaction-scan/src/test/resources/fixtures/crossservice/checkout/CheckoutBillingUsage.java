package fixtures.checkout;

public class CheckoutBillingUsage {
    private FeatureFlags flags;

    void apply() {
        boolean limited = flags.isEnabled(BillingFlags.RATE_LIMIT);
    }
}

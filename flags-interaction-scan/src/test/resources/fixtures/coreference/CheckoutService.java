package fixtures.checkout;

public class CheckoutService {
    private FeatureFlags flags;

    void render() {
        boolean a = flags.isEnabled(CheckoutFlags.NEW_CHECKOUT);
        boolean b = flags.isEnabled(CheckoutFlags.EXPRESS_PAY);
    }
}

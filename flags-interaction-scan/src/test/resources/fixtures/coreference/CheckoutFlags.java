package fixtures.checkout;

public enum CheckoutFlags implements FlagKey {
    NEW_CHECKOUT("checkout.new-checkout"),
    EXPRESS_PAY("checkout.express-pay");

    private final String key;

    CheckoutFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}

package fixtures.orders;

public enum OrdersFlags implements FlagKey {
    SPLIT_SHIPMENT("orders.split-shipment"),
    GIFT_WRAP("orders.gift-wrap");

    private final String key;

    OrdersFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}

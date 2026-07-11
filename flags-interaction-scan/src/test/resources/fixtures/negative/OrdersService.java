package fixtures.orders;

public class OrdersService {
    private FeatureFlags flags;

    void ship() {
        if (flags.isEnabled(OrdersFlags.SPLIT_SHIPMENT)) {
            doShip();
        }
    }

    void wrap() {
        boolean w = flags.isEnabled(OrdersFlags.GIFT_WRAP);
    }

    void doShip() {
    }
}

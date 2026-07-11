package fixtures.search;

public enum SearchFlags implements FlagKey {
    NEW_RANKING("search.new-ranking"),
    TYPO_TOLERANCE("search.typo-tolerance");

    private final String key;

    SearchFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}

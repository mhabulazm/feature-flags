package fixtures.search;

public class SearchService {
    private FeatureFlags flags;

    void search() {
        if (flags.isEnabled(SearchFlags.NEW_RANKING)) {
            if (flags.isEnabled(SearchFlags.TYPO_TOLERANCE)) {
                doSearch();
            }
        }
    }

    void doSearch() {
    }
}

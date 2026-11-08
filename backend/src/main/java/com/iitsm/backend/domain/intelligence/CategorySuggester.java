package com.iitsm.backend.domain.intelligence;

import com.iitsm.backend.domain.catalog.Category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <b>Intelligent feature: automatic categorization.</b> Suggests the category of a new incident from its title and
 * description, combining two independent signals:
 *
 * <ol>
 *   <li><b>Keywords</b> the administrator attached to each category ("vpn", "packet loss" → Network). A match in
 *       the title counts {@value #TITLE_KEYWORD_POINTS} points, in the description {@value #BODY_KEYWORD_POINTS}.
 *       The category's own name counts as a keyword.</li>
 *   <li><b>History (k-nearest neighbours):</b> the {@value #NEIGHBOURS} most similar past incidents that already
 *       have a category vote for it, weighted by their TF-IDF similarity × {@value #HISTORY_WEIGHT}. The system
 *       therefore learns from how support staff actually classified incidents, with no training step.</li>
 * </ol>
 *
 * <p><b>Confidence</b> = (share of all points that went to this category) × (strength of the evidence, capped at
 * 1). A single weak keyword hit gives low confidence even if no other category matched.
 */
public final class CategorySuggester {

    static final int TITLE_KEYWORD_POINTS = 2;
    static final int BODY_KEYWORD_POINTS = 1;
    static final int NEIGHBOURS = 5;
    static final double HISTORY_WEIGHT = 3.0;
    /** Past incidents less similar than this are ignored as neighbours. */
    static final double MIN_NEIGHBOUR_SIMILARITY = 0.2;
    /** Points at which the evidence counts as strong (strength factor reaches 1). */
    static final double STRONG_EVIDENCE_POINTS = 3.0;
    static final int MAX_SUGGESTIONS = 3;

    /** A past incident whose category is known. */
    public record LabelledText(String text, Category category) {
    }

    public record Suggestion(Category category, double confidence, List<String> reasons) {
    }

    /**
     * @param categories the categories that may be suggested (normally the active ones)
     * @param history    previously classified incidents, used as neighbours
     * @return up to {@value #MAX_SUGGESTIONS} suggestions, most confident first; empty if nothing matched
     */
    public List<Suggestion> suggest(String title, String description, Collection<Category> categories,
                                    Collection<LabelledText> history) {
        Map<Category, Evidence> evidence = new LinkedHashMap<>();
        for (Category category : categories) {
            evidence.put(category, new Evidence());
        }

        scoreKeywords(title, description, evidence);
        scoreHistory(title + "\n" + description, history, evidence);

        double total = evidence.values().stream().mapToDouble(e -> e.points).sum();
        if (total == 0) {
            return List.of();
        }
        return evidence.entrySet().stream()
                .filter(entry -> entry.getValue().points > 0)
                .map(entry -> {
                    double points = entry.getValue().points;
                    double share = points / total;
                    double strength = Math.min(1.0, points / STRONG_EVIDENCE_POINTS);
                    return new Suggestion(entry.getKey(), round(share * strength), entry.getValue().reasons());
                })
                .sorted(Comparator.comparingDouble(Suggestion::confidence).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static void scoreKeywords(String title, String description, Map<Category, Evidence> evidence) {
        String titlePhrase = TextAnalyzer.normalizeForPhraseSearch(title);
        String bodyPhrase = TextAnalyzer.normalizeForPhraseSearch(description);
        Set<String> titleTerms = new HashSet<>(TextAnalyzer.terms(title));
        Set<String> bodyTerms = new HashSet<>(TextAnalyzer.terms(description));

        evidence.forEach((category, e) -> {
            Set<String> keywords = new HashSet<>(category.getKeywords());
            keywords.add(category.getName().toLowerCase(Locale.ROOT));
            for (String keyword : keywords) {
                if (matches(keyword, titlePhrase, titleTerms)) {
                    e.add(TITLE_KEYWORD_POINTS, keyword);
                } else if (matches(keyword, bodyPhrase, bodyTerms)) {
                    e.add(BODY_KEYWORD_POINTS, keyword);
                }
            }
        });
    }

    /**
     * Multi-word keywords must appear as a phrase; single words also match other forms of the word
     * ("printers" matches "printer").
     */
    private static boolean matches(String keyword, String phraseText, Set<String> terms) {
        String normalized = TextAnalyzer.normalizeForPhraseSearch(keyword);
        if (phraseText.contains(normalized)) {
            return true;
        }
        List<String> keywordTerms = TextAnalyzer.terms(keyword);
        return keywordTerms.size() == 1 && terms.contains(keywordTerms.getFirst());
    }

    private static void scoreHistory(String text, Collection<LabelledText> history, Map<Category, Evidence> evidence) {
        List<TextSimilarity.Scored<LabelledText>> neighbours = TextSimilarity.rank(text, history, LabelledText::text)
                .stream()
                .filter(s -> s.score() >= MIN_NEIGHBOUR_SIMILARITY)
                .limit(NEIGHBOURS)
                .toList();
        for (TextSimilarity.Scored<LabelledText> neighbour : neighbours) {
            Evidence e = evidence.get(neighbour.document().category());
            if (e != null) {
                e.addNeighbour(neighbour.score() * HISTORY_WEIGHT);
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Points collected for one category, and why. */
    private static final class Evidence {
        private double points;
        private final List<String> keywords = new ArrayList<>();
        private int neighbours;

        void add(int keywordPoints, String keyword) {
            points += keywordPoints;
            keywords.add(keyword);
        }

        void addNeighbour(double weight) {
            points += weight;
            neighbours++;
        }

        List<String> reasons() {
            List<String> reasons = new ArrayList<>();
            if (!keywords.isEmpty()) {
                reasons.add("Mentions " + String.join(", ", keywords.stream().sorted().map(k -> "\"" + k + "\"")
                        .toList()));
            }
            if (neighbours > 0) {
                reasons.add(neighbours + " similar past incident" + (neighbours == 1 ? " was" : "s were")
                        + " classified this way");
            }
            return reasons;
        }
    }
}

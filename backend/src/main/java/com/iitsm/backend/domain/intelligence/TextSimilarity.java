package com.iitsm.backend.domain.intelligence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Text similarity with <b>TF-IDF vectors and cosine similarity</b>, the classic information-retrieval technique.
 *
 * <ul>
 *   <li><b>TF (term frequency):</b> a word used often in one text matters more for that text.</li>
 *   <li><b>IDF (inverse document frequency):</b> a word that appears in nearly every incident ("error", "user")
 *       says little; a rare one ("deadlock", "vpn") says a lot. Rare words get a higher weight.</li>
 *   <li><b>Cosine similarity:</b> the angle between two weighted word vectors, from 0 (nothing in common) to 1
 *       (same words in the same proportions). Independent of text length, so a short title can match a long
 *       description.</li>
 * </ul>
 *
 * <p>The IDF weights are computed from the corpus passed in, so the engine adapts to the organization's own
 * vocabulary without any training step.
 */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    /** One corpus document with its similarity to the query. */
    public record Scored<D>(D document, double score, List<String> sharedTerms) {
    }

    /**
     * Scores every document in {@code corpus} against {@code query}, best first. Documents with a score of 0 are
     * left out.
     *
     * @param text how to get the text of a document (e.g. title + description of an incident)
     */
    public static <D> List<Scored<D>> rank(String query, Collection<D> corpus, Function<D, String> text) {
        List<String> queryTerms = TextAnalyzer.terms(query);
        if (queryTerms.isEmpty() || corpus.isEmpty()) {
            return List.of();
        }
        List<D> documents = new ArrayList<>(corpus);
        List<List<String>> documentTerms = documents.stream().map(d -> TextAnalyzer.terms(text.apply(d))).toList();

        Map<String, Double> idf = inverseDocumentFrequency(queryTerms, documentTerms);
        Map<String, Double> queryVector = weigh(queryTerms, idf);

        List<Scored<D>> results = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Map<String, Double> documentVector = weigh(documentTerms.get(i), idf);
            double score = cosine(queryVector, documentVector);
            if (score > 0) {
                results.add(new Scored<>(documents.get(i), score, sharedTerms(queryVector, documentVector)));
            }
        }
        results.sort(Comparator.comparingDouble((Scored<D> s) -> s.score()).reversed());
        return results;
    }

    /** Similarity of two texts on their own (IDF from just these two, so it is mostly term overlap). */
    public static double similarity(String a, String b) {
        List<Scored<String>> ranked = rank(a, List.of(b), Function.identity());
        return ranked.isEmpty() ? 0.0 : ranked.getFirst().score();
    }

    /** Smoothed IDF: ln((N + 1) / (df + 1)) + 1, counting the query as a document too. Never zero or negative. */
    private static Map<String, Double> inverseDocumentFrequency(List<String> query, List<List<String>> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        countOnce(query, documentFrequency);
        documents.forEach(terms -> countOnce(terms, documentFrequency));
        int n = documents.size() + 1;
        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((term, df) -> idf.put(term, Math.log((n + 1.0) / (df + 1.0)) + 1.0));
        return idf;
    }

    private static void countOnce(List<String> terms, Map<String, Integer> documentFrequency) {
        for (String term : new HashSet<>(terms)) {
            documentFrequency.merge(term, 1, Integer::sum);
        }
    }

    /** TF × IDF, with sub-linear TF (1 + ln tf) so repeating a word ten times doesn't dominate. */
    private static Map<String, Double> weigh(List<String> terms, Map<String, Double> idf) {
        Map<String, Integer> tf = new HashMap<>();
        terms.forEach(term -> tf.merge(term, 1, Integer::sum));
        Map<String, Double> vector = new HashMap<>();
        tf.forEach((term, count) -> vector.put(term, (1 + Math.log(count)) * idf.getOrDefault(term, 1.0)));
        return vector;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            Double other = b.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
        }
        if (dot == 0) {
            return 0;
        }
        return dot / (norm(a) * norm(b));
    }

    private static double norm(Map<String, Double> vector) {
        double sum = 0;
        for (double weight : vector.values()) {
            sum += weight * weight;
        }
        return Math.sqrt(sum);
    }

    /** The most important shared terms (highest combined weight), for explaining a match to the user. */
    private static List<String> sharedTerms(Map<String, Double> a, Map<String, Double> b) {
        Set<String> shared = new HashSet<>(a.keySet());
        shared.retainAll(b.keySet());
        return shared.stream()
                .sorted(Comparator.comparingDouble((String t) -> a.get(t) * b.get(t)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .limit(5)
                .toList();
    }
}

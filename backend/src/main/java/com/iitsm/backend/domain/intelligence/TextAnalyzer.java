package com.iitsm.backend.domain.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns free text ("VPN keeps disconnecting since this morning!") into comparable terms
 * ({@code [vpn, keep, disconnect, morn]}).
 *
 * <p>Steps: lower-case → split on anything that is not a letter or digit → drop stop words and one-letter tokens →
 * light suffix stemming, so "disconnecting", "disconnected" and "disconnects" become the same term.
 *
 * <p>The stemmer is deliberately simple (a few English suffixes). It is predictable and explainable in the report,
 * which matters more here than linguistic perfection.
 */
public final class TextAnalyzer {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Common English words that carry no meaning for matching incidents. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "after", "again", "all", "also", "am", "an", "and", "any", "are", "as", "at", "be",
            "been", "before", "being", "but", "by", "can", "cannot", "could", "did", "do", "does", "doing", "dont",
            "for", "from", "get", "getting", "got", "had", "has", "have", "having", "he", "her", "here", "him",
            "his", "how", "i", "if", "im", "in", "into", "is", "it", "its", "just", "me", "my", "no", "not", "now",
            "of", "on", "once", "only", "or", "our", "out", "please", "same", "she", "should", "since", "so",
            "some", "still", "than", "that", "the", "their", "them", "then", "there", "these", "they", "this",
            "those", "to", "too", "today", "up", "us", "very", "was", "we", "were", "what", "when", "where",
            "which", "while", "who", "why", "will", "with", "would", "you", "your", "hi", "hello", "thanks",
            "help", "issue", "problem", "working", "work", "works");

    private TextAnalyzer() {
    }

    /** The meaningful, stemmed terms of {@code text}, in order (duplicates kept: they count for term frequency). */
    public static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String token : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            result.add(stem(token));
        }
        return result;
    }

    /**
     * Lower-cases and collapses everything that is not a letter or digit to single spaces, padded with a space on
     * each side. Lets multi-word keywords ("packet loss") be found with a plain {@code contains(" packet loss ")}
     * check that never matches inside a longer word.
     */
    public static String normalizeForPhraseSearch(String text) {
        if (text == null) {
            return " ";
        }
        return " " + NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip() + " ";
    }

    private static final String[] SUFFIXES = {"ations", "ation", "ings", "ing", "edly", "ed", "ies", "es", "ly", "s"};

    /**
     * Removes a common suffix if enough of the word remains, then a final "e" and a doubled final consonant, so
     * every form of a word meets at the same stem: update/updated/updates/updating → "updat", service/services →
     * "servic", drop/dropped → "drop". Numbers are left alone.
     */
    static String stem(String token) {
        if (token.chars().allMatch(Character::isDigit)) {
            return token;
        }
        String word = token;
        for (String suffix : SUFFIXES) {
            if (word.endsWith(suffix) && word.length() - suffix.length() >= 3) {
                String base = word.substring(0, word.length() - suffix.length());
                word = suffix.equals("ies") ? base + "y" : base;
                break;
            }
        }
        if (word.length() > 3 && word.endsWith("e")) {
            word = word.substring(0, word.length() - 1);
        }
        int n = word.length();
        if (n > 3 && word.charAt(n - 1) == word.charAt(n - 2) && !isVowel(word.charAt(n - 1))
                && Character.isLetter(word.charAt(n - 1))) {
            word = word.substring(0, n - 1);
        }
        return word;
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }
}

package com.iitsm.backend.domain.intelligence;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.intelligence.CategorySuggester.LabelledText;
import com.iitsm.backend.domain.intelligence.CategorySuggester.Suggestion;
import com.iitsm.backend.domain.intelligence.IncidentCorrelator.Correlation;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder.Match;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder.Probe;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder.Relation;
import com.iitsm.backend.domain.user.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.category;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static com.iitsm.backend.domain.TestFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;

/** The intelligent features: text analysis, category suggestion, duplicate detection, root-cause correlation. */
class IntelligenceTest {

    private static final AtomicInteger NUMBERS = new AtomicInteger();

    private Employee reporter;
    private ItService database;
    private ItService payment;
    private ItService email;
    private ItService vpn;

    @BeforeEach
    void setUp() {
        reporter = employee("Asha Rao");
        database = service("Customer Database", BusinessCriticality.MISSION_CRITICAL);
        payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        email = service("Email", BusinessCriticality.MEDIUM);
        vpn = service("VPN", BusinessCriticality.HIGH);
        payment.addDependency(database);
        email.addDependency(database);
    }

    private Incident incident(String title, String description, ItService service) {
        return Incident.builder()
                .number(String.format("INC-2026-%06d", NUMBERS.incrementAndGet()))
                .title(title)
                .description(description)
                .reporter(reporter)
                .service(service)
                .impact(Impact.MEDIUM)
                .urgency(Urgency.HIGH)
                .affectedUsers(10)
                .reportedAt(T0)
                .build();
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Text analysis")
    class TextAnalysis {

        @Test
        @DisplayName("different forms of a word reduce to the same term")
        void stemming() {
            assertThat(TextAnalyzer.terms("update updated updates updating"))
                    .containsOnly(TextAnalyzer.stem("update"));
            assertThat(TextAnalyzer.stem("services")).isEqualTo(TextAnalyzer.stem("service"));
            assertThat(TextAnalyzer.stem("dropped")).isEqualTo("drop");
            assertThat(TextAnalyzer.stem("500")).isEqualTo("500");
        }

        @Test
        @DisplayName("stop words and punctuation carry no meaning and are dropped")
        void stopWords() {
            assertThat(TextAnalyzer.terms("The VPN is not working, please help!!")).containsExactly("vpn");
        }

        @Test
        @DisplayName("identical texts score 1, unrelated texts 0, similar texts in between")
        void cosineSimilarity() {
            String vpnDown = "VPN disconnects every few minutes";
            assertThat(TextSimilarity.similarity(vpnDown, vpnDown)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(TextSimilarity.similarity(vpnDown, "Printer out of toner")).isZero();
            double similar = TextSimilarity.similarity(vpnDown, "VPN keeps disconnecting");
            assertThat(similar).isBetween(0.3, 0.99);
        }

        @Test
        @DisplayName("rare words weigh more than common ones (IDF)")
        void rareWordsMatter() {
            List<String> corpus = List.of("email error on login", "printer error", "deadlock error in database");
            var ranked = TextSimilarity.rank("database deadlock error", corpus, s -> s);
            assertThat(ranked.getFirst().document()).isEqualTo("deadlock error in database");
            assertThat(ranked.getFirst().sharedTerms()).contains("deadlock");
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Category suggestion")
    class CategorySuggestions {

        private Category network;
        private Category hardware;
        private Category database;
        private List<Category> categories;
        private final CategorySuggester suggester = new CategorySuggester();

        @BeforeEach
        void setUp() {
            network = category("Network");
            network.addKeyword("vpn");
            network.addKeyword("packet loss");
            hardware = category("Hardware");
            hardware.addKeyword("printer");
            hardware.addKeyword("laptop");
            database = category("Database");
            database.addKeyword("deadlock");
            categories = List.of(network, hardware, database);
        }

        @Test
        @DisplayName("a keyword in the title gives a confident suggestion with the reason")
        void keywordInTitle() {
            List<Suggestion> suggestions = suggester.suggest("VPN drops every hour",
                    "Since this morning my connection to the office drops and there is packet loss", categories, List.of());

            assertThat(suggestions.getFirst().category()).isSameAs(network);
            assertThat(suggestions.getFirst().confidence()).isGreaterThanOrEqualTo(0.5);
            assertThat(suggestions.getFirst().reasons().getFirst()).contains("\"vpn\"", "\"packet loss\"");
        }

        @Test
        @DisplayName("single-word keywords also match other forms (printers → printer)")
        void pluralMatches() {
            List<Suggestion> suggestions = suggester.suggest("Printers on floor 3", "None of the printers respond",
                    categories, List.of());
            assertThat(suggestions.getFirst().category()).isSameAs(hardware);
        }

        @Test
        @DisplayName("history: similar past incidents vote even when no keyword matches")
        void learnsFromHistory() {
            List<LabelledText> history = List.of(
                    new LabelledText("Orders table locked, queries hang for minutes", database),
                    new LabelledText("Reporting queries hang, table locked", database),
                    new LabelledText("Laptop screen flickers", hardware));

            List<Suggestion> suggestions = suggester.suggest("Queries hang", "The invoices table seems locked",
                    categories, history);

            assertThat(suggestions.getFirst().category()).isSameAs(database);
            assertThat(suggestions.getFirst().reasons()).anyMatch(r -> r.contains("similar past incident"));
        }

        @Test
        @DisplayName("nothing recognisable → no suggestion; one weak hit → low confidence")
        void weakEvidence() {
            assertThat(suggester.suggest("Something odd", "It behaves strangely", categories, List.of())).isEmpty();

            List<Suggestion> weak = suggester.suggest("Slow", "maybe the laptop is old", categories, List.of());
            assertThat(weak.getFirst().category()).isSameAs(hardware);
            assertThat(weak.getFirst().confidence()).isLessThan(0.5);
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Duplicate and related incidents")
    class RelatedIncidents {

        private final RelatedIncidentFinder finder = new RelatedIncidentFinder();

        @Test
        @DisplayName("a near-identical open incident on the same service is a likely duplicate")
        void likelyDuplicate() {
            Incident existing = incident("Payment page shows error 500",
                    "Checkout fails with error 500 after entering card details", payment);
            Incident unrelated = incident("Printer jam", "The printer on floor 2 jams on every page", vpn);

            List<Match> matches = finder.find(new Probe(null, "Error 500 on payment page",
                    "Customers get error 500 at checkout when entering card details", payment), List.of(existing, unrelated));

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().incident()).isSameAs(existing);
            assertThat(matches.getFirst().relation()).isEqualTo(Relation.LIKELY_DUPLICATE);
            assertThat(matches.getFirst().reasons()).anyMatch(r -> r.startsWith("Same service"));
        }

        @Test
        @DisplayName("a similar incident that is no longer open is only 'similar', never a duplicate")
        void closedIsOnlySimilar() {
            Incident old = incident("Payment page shows error 500",
                    "Checkout fails with error 500 after entering card details", payment);
            old.cancel("Fixed by restart", minutesAfterT0(30));

            List<Match> matches = finder.find(new Probe(null, "Error 500 on payment page",
                    "Checkout fails with error 500", payment), List.of(old));

            assertThat(matches.getFirst().relation()).isEqualTo(Relation.SIMILAR);
        }

        @Test
        @DisplayName("services linked through the dependency graph earn a bonus")
        void linkedServices() {
            assertThat(RelatedIncidentFinder.servicesLinked(payment, database)).isTrue();
            assertThat(RelatedIncidentFinder.servicesLinked(payment, email)).isTrue();   // both use the database
            assertThat(RelatedIncidentFinder.servicesLinked(payment, vpn)).isFalse();
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Root-cause correlation")
    class Correlations {

        private final IncidentCorrelator correlator = new IncidentCorrelator();

        @Test
        @DisplayName("incidents on services that share a dependency point at that dependency")
        void sharedDependency() {
            List<Incident> open = List.of(
                    incident("Checkout timing out", "Payments hang", payment),
                    incident("Refunds failing", "Refund page errors", payment),
                    incident("Mailbox search broken", "Search in email returns nothing", email));

            List<Correlation> correlations = correlator.correlate(open);

            assertThat(correlations).hasSize(1);
            assertThat(correlations.getFirst().rootService()).isSameAs(database);
            assertThat(correlations.getFirst().affectedServices()).containsExactly("Email", "Payment");
            assertThat(correlations.getFirst().explanation()).contains("Check Customer Database first");
        }

        @Test
        @DisplayName("many incidents on one service blame that service, not its dependencies")
        void singleService() {
            List<Incident> open = List.of(
                    incident("Checkout timing out", "Payments hang", payment),
                    incident("Refunds failing", "Refund page errors", payment),
                    incident("Card declined wrongly", "Valid cards declined", payment));

            List<Correlation> correlations = correlator.correlate(open);

            assertThat(correlations).hasSize(1);
            assertThat(correlations.getFirst().rootService()).isSameAs(payment);
        }

        @Test
        @DisplayName("fewer than three incidents is not a pattern")
        void tooFew() {
            assertThat(correlator.correlate(List.of(
                    incident("Checkout timing out", "Payments hang", payment),
                    incident("Mailbox search broken", "Search returns nothing", email)))).isEmpty();
        }
    }
}

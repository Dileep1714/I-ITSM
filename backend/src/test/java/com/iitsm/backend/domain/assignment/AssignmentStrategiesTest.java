package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.category;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static com.iitsm.backend.domain.TestFixtures.service;
import static com.iitsm.backend.domain.TestFixtures.technician;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team used throughout (capacity 5 each):
 * <pre>
 *   Anna  Network skill 5   4 of 5 busy
 *   Bala  Network skill 3   0 of 5 busy
 *   Chen  no Network skill  1 of 5 busy
 *   Dev   Network skill 5   OFF DUTY
 * </pre>
 */
class AssignmentStrategiesTest {

    private Category network;
    private ItService vpn;
    private Employee reporter;
    private Technician anna;
    private Technician bala;
    private Technician chen;
    private Technician dev;
    private Incident incident;
    private List<AssignmentCandidate> team;

    @BeforeEach
    void setUp() {
        network = category("Network");
        vpn = service("VPN", BusinessCriticality.HIGH);
        reporter = employee("Asha Rao");

        anna = technician("Anna Das", 5);
        bala = technician("Bala Nair", 5);
        chen = technician("Chen Li", 5);
        dev = technician("Dev Patel", 5);
        anna.addOrUpdateSkill(network, 5);
        bala.addOrUpdateSkill(network, 3);
        dev.addOrUpdateSkill(network, 5);
        dev.changeAvailability(Availability.OFF_DUTY);

        incident = classifiedIncident(PriorityLevel.P3);
        team = List.of(
                new AssignmentCandidate(anna, 4, 0),
                new AssignmentCandidate(bala, 0, 0),
                new AssignmentCandidate(chen, 1, 0),
                new AssignmentCandidate(dev, 0, 0));
    }

    private Incident classifiedIncident(PriorityLevel level) {
        Incident created = Incident.builder()
                .number("INC-2026-000100")
                .title("VPN disconnects every few minutes")
                .description("Remote staff lose the VPN connection repeatedly.")
                .reporter(reporter).service(vpn)
                .impact(Impact.MEDIUM).urgency(Urgency.HIGH).affectedUsers(30)
                .reportedAt(T0)
                .build();
        created.classify(network, new Priority(level, 10, "test"),
                SlaPolicy.standard(level, Duration.ofHours(1), Duration.ofHours(8)), minutesAfterT0(1));
        return created;
    }

    private static List<Technician> order(List<TechnicianScore> ranking) {
        return ranking.stream().map(TechnicianScore::technician).toList();
    }

    @Test
    @DisplayName("Skill-based: best skill first; unskilled and off-duty technicians are excluded")
    void skillBased() {
        List<TechnicianScore> ranking = new SkillBasedStrategy().rank(incident, team);

        assertThat(order(ranking)).containsExactly(anna, bala);
        assertThat(ranking.get(0).score()).isEqualTo(1.0);
        assertThat(ranking.get(0).reasons()).containsExactly("Network skill 5/5");
    }

    @Test
    @DisplayName("Load-based: least busy first, regardless of skill; off-duty excluded")
    void loadBased() {
        List<TechnicianScore> ranking = new LoadBasedStrategy().rank(incident, team);

        assertThat(order(ranking)).containsExactly(bala, chen, anna);
        assertThat(ranking.get(2).reasons()).containsExactly("4 of 5 slots in use");
    }

    @Test
    @DisplayName("A technician at full capacity is never recommended")
    void fullTechnicianExcluded() {
        List<AssignmentCandidate> annaFull = List.of(new AssignmentCandidate(anna, 5, 0),
                new AssignmentCandidate(bala, 0, 0));

        assertThat(order(new SkillBasedStrategy().rank(incident, annaFull))).containsExactly(bala);
    }

    @Test
    @DisplayName("When reassigning, the current assignee is not recommended again")
    void currentAssigneeExcluded() {
        incident.assign(anna, 4, minutesAfterT0(2));

        assertThat(order(new SkillBasedStrategy().rank(incident, team))).containsExactly(bala);
    }

    @Test
    @DisplayName("Nobody eligible → empty ranking (the service layer then escalates)")
    void nobodyEligible() {
        List<AssignmentCandidate> onlyDev = List.of(new AssignmentCandidate(dev, 0, 0));

        assertThat(new SkillBasedStrategy().recommend(incident, onlyDev)).isEmpty();
        assertThat(new LoadBasedStrategy().recommend(incident, onlyDev)).isEmpty();
        assertThat(new RoundRobinStrategy().recommend(incident, onlyDev)).isEmpty();
    }

    @Test
    @DisplayName("Equal scores are broken by lower workload")
    void tieBrokenByWorkload() {
        bala.addOrUpdateSkill(network, 5); // now Anna and Bala both have skill 5

        assertThat(order(new SkillBasedStrategy().rank(incident, team))).containsExactly(bala, anna);
    }

    @Test
    @DisplayName("Experience-based: past fixes on the same service outweigh general seniority")
    void experienceBased() {
        List<AssignmentCandidate> candidates = List.of(
                new AssignmentCandidate(anna, 0, 0),   // 3 yrs, never fixed VPN
                new AssignmentCandidate(bala, 0, 8));  // 3 yrs, fixed VPN 8 times

        List<TechnicianScore> ranking = new ExperienceBasedStrategy().rank(incident, candidates);

        assertThat(order(ranking)).containsExactly(bala, anna);
        assertThat(ranking.get(0).reasons().get(0)).contains("resolved 8 VPN incidents");
    }

    @Nested
    @DisplayName("Smart composite strategy")
    class Smart {

        @Test
        @DisplayName("Balances skill against workload: a free, decent technician beats a busy expert")
        void balancesSkillAndLoad() {
            // Anna: 0.4×1.0 + 0.3×0.2 = 0.46 + exp;  Bala: 0.4×0.6 + 0.3×1.0 = 0.54 + exp (same exp)
            List<TechnicianScore> ranking = SmartCompositeStrategy.standard().rank(incident, team);

            assertThat(ranking.get(0).technician()).isSameAs(bala);
            assertThat(ranking.get(0).reasons().get(0))
                    .contains("Network skill 3/5", "0 of 5 slots in use", "VPN incidents");
        }

        @Test
        @DisplayName("Considers technicians without the skill (they can still win on other factors)")
        void includesUnskilled() {
            assertThat(order(SmartCompositeStrategy.standard().rank(incident, team))).contains(chen);
        }

        @Test
        @DisplayName("For a P1, experience with the service counts more and can change the recommendation")
        void criticalIncidentsFavourExperience() {
            anna.addOrUpdateSkill(network, 3);
            anna.updateExperience(8);
            List<AssignmentCandidate> candidates = List.of(
                    new AssignmentCandidate(anna, 4, 8),   // busy, but has fixed VPN 8 times
                    new AssignmentCandidate(bala, 0, 0));  // free, no VPN history

            Incident p3 = classifiedIncident(PriorityLevel.P3);
            Incident p1 = classifiedIncident(PriorityLevel.P1);
            SmartCompositeStrategy smart = SmartCompositeStrategy.standard();

            assertThat(smart.recommend(p3, candidates)).get()
                    .extracting(TechnicianScore::technician).isSameAs(bala);
            assertThat(smart.recommend(p1, candidates)).get()
                    .extracting(TechnicianScore::technician).isSameAs(anna);
        }
    }

    @Nested
    @DisplayName("Round-robin strategy")
    class RoundRobin {

        private final RoundRobinStrategy strategy = new RoundRobinStrategy();

        @Test
        @DisplayName("Takes turns in a fixed order and wraps around after the last technician")
        void rotates() {
            assertThat(strategy.recommend(incident, team)).get()
                    .extracting(TechnicianScore::technician).isSameAs(anna);

            strategy.onAssigned(anna);
            assertThat(strategy.recommend(incident, team)).get()
                    .extracting(TechnicianScore::technician).isSameAs(bala);

            strategy.onAssigned(chen);
            assertThat(strategy.recommend(incident, team)).get()
                    .extracting(TechnicianScore::technician).isSameAs(anna);
        }

        @Test
        @DisplayName("Skips technicians who are unavailable or full")
        void skipsIneligible() {
            List<AssignmentCandidate> balaFull = List.of(
                    new AssignmentCandidate(anna, 0, 0),
                    new AssignmentCandidate(bala, 5, 0),
                    new AssignmentCandidate(chen, 0, 0),
                    new AssignmentCandidate(dev, 0, 0));
            strategy.onAssigned(anna);

            assertThat(order(strategy.rank(incident, balaFull))).containsExactly(chen, anna);
        }
    }
}

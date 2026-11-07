package com.iitsm.backend.domain;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.FakePasswordHasher;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Ready-made domain objects for tests, so each test only states what is special about it. */
public final class TestFixtures {

    /** A fixed point in time. Every test computes its times from here, so results never depend on the clock. */
    public static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    public static final PasswordHasher HASHER = new FakePasswordHasher();

    private TestFixtures() {
    }

    public static Instant minutesAfterT0(long minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    public static Employee employee(String fullName) {
        return new Employee(fullName, emailFor(fullName), "secret123", HASHER, "Finance", "Hyderabad");
    }

    public static Technician technician(String fullName, int maxActiveIncidents) {
        return new Technician(fullName, emailFor(fullName), "secret123", HASHER,
                SupportLevel.L1_TECHNICIAN, 3, maxActiveIncidents);
    }

    public static Category category(String name) {
        return new Category(name, name + " issues");
    }

    public static ItService service(String name, BusinessCriticality criticality) {
        return new ItService(name, name + " service", criticality);
    }

    /** P1: respond within 5 minutes, resolve within 30, as in the spec's example table. */
    public static SlaPolicy p1Policy() {
        return SlaPolicy.standard(PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
    }

    public static Priority p1Priority() {
        return new Priority(PriorityLevel.P1, 48, "Impact HIGH × Urgency CRITICAL × Criticality MISSION_CRITICAL");
    }

    private static String emailFor(String fullName) {
        return fullName.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
    }
}

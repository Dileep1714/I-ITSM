package com.iitsm.backend.domain.catalog;

import com.iitsm.backend.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.iitsm.backend.domain.TestFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The service dependency graph used for incident correlation. */
class ItServiceTest {

    private ItService payment;
    private ItService authentication;
    private ItService database;

    @BeforeEach
    void setUp() {
        payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        authentication = service("Authentication", BusinessCriticality.HIGH);
        database = service("Database", BusinessCriticality.MISSION_CRITICAL);
    }

    @Test
    @DisplayName("Dependencies are followed through chains: Payment → Authentication → Database")
    void transitiveDependencies() {
        payment.addDependency(authentication);
        authentication.addDependency(database);

        assertThat(payment.dependsOnTransitively(authentication)).isTrue();
        assertThat(payment.dependsOnTransitively(database)).isTrue();
        assertThat(database.dependsOnTransitively(payment)).isFalse();
    }

    @Test
    @DisplayName("A service cannot depend on itself")
    void selfDependencyRejected() {
        assertThatThrownBy(() -> payment.addDependency(payment))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("CIRCULAR_DEPENDENCY");
    }

    @Test
    @DisplayName("Direct and indirect cycles are rejected")
    void cyclesRejected() {
        payment.addDependency(authentication);
        authentication.addDependency(database);

        assertThatThrownBy(() -> authentication.addDependency(payment)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> database.addDependency(payment)).isInstanceOf(BusinessRuleException.class);
        assertThat(database.getDependsOn()).isEmpty();
    }

    @Test
    @DisplayName("New services are OPERATIONAL and their dependency set is read-only")
    void defaultsAndEncapsulation() {
        assertThat(payment.getStatus()).isEqualTo(ServiceStatus.OPERATIONAL);
        assertThatThrownBy(() -> payment.getDependsOn().add(database))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

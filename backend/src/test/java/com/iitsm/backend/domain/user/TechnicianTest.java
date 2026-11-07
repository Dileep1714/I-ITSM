package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.catalog.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TechnicianTest {

    private final PasswordHasher hasher = new FakePasswordHasher();
    private final Category network = new Category("Network", "LAN, WAN, VPN");
    private final Category database = new Category("Database", "Database servers");

    private Technician technician;

    @BeforeEach
    void setUp() {
        technician = new Technician("Ravi Kumar", "ravi@example.com", "secret123", hasher,
                SupportLevel.L1_TECHNICIAN, 3, 3);
    }

    @Test
    @DisplayName("A new technician is available, has no skills and the TECHNICIAN role")
    void newTechnicianDefaults() {
        assertThat(technician.getAvailability()).isEqualTo(Availability.AVAILABLE);
        assertThat(technician.getSkills()).isEmpty();
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    @DisplayName("Skills determine which categories a technician can handle, and how well")
    void skillsDetermineCapability() {
        technician.addOrUpdateSkill(network, 4);

        assertThat(technician.canHandle(network)).isTrue();
        assertThat(technician.proficiencyIn(network)).isEqualTo(4);
        assertThat(technician.canHandle(database)).isFalse();
        assertThat(technician.proficiencyIn(database)).isZero();
    }

    @Test
    @DisplayName("Adding a skill for an existing category updates it instead of duplicating it")
    void addingExistingSkillUpdatesIt() {
        technician.addOrUpdateSkill(network, 2);
        technician.addOrUpdateSkill(network, 5);

        assertThat(technician.getSkills()).hasSize(1);
        assertThat(technician.proficiencyIn(network)).isEqualTo(5);
    }

    @ParameterizedTest(name = "proficiency {0} is rejected")
    @ValueSource(ints = {0, 6, -1})
    @DisplayName("Proficiency outside 1–5 is rejected and leaves existing skills untouched")
    void invalidProficiencyRejected(int proficiency) {
        technician.addOrUpdateSkill(network, 3);

        assertThatThrownBy(() -> technician.addOrUpdateSkill(network, proficiency))
                .isInstanceOf(InvalidInputException.class);
        assertThat(technician.proficiencyIn(network)).isEqualTo(3);
    }

    @Test
    @DisplayName("Proficiency boundaries 1 and 5 are accepted")
    void proficiencyBoundariesAccepted() {
        technician.addOrUpdateSkill(network, 1);
        technician.addOrUpdateSkill(database, 5);

        assertThat(technician.proficiencyIn(network)).isEqualTo(1);
        assertThat(technician.proficiencyIn(database)).isEqualTo(5);
    }

    @Test
    @DisplayName("Capacity boundary: with a limit of 3, two active incidents fit and three do not")
    void capacityBoundary() {
        assertThat(technician.hasCapacity(2)).isTrue();
        assertThat(technician.hasCapacity(3)).isFalse();
        assertThat(technician.utilization(3)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("An unavailable or deactivated technician is not assignable even with spare capacity")
    void unavailableTechnicianNotAssignable() {
        assertThat(technician.isAssignable(0)).isTrue();

        technician.changeAvailability(Availability.OFF_DUTY);
        assertThat(technician.isAssignable(0)).isFalse();

        technician.changeAvailability(Availability.AVAILABLE);
        technician.deactivate();
        assertThat(technician.isAssignable(0)).isFalse();
    }

    @Test
    @DisplayName("The skills list cannot be modified from outside (encapsulation)")
    void skillsAreReadOnly() {
        assertThatThrownBy(() -> technician.getSkills().add(new Skill(network, 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Invalid capacity and experience values are rejected")
    void invalidNumbersRejected() {
        assertThatThrownBy(() -> technician.changeCapacity(0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> technician.updateExperience(-1)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> technician.hasCapacity(-1)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Support levels form an escalation chain ending at IT Manager")
    void supportLevelChain() {
        assertThat(SupportLevel.L1_TECHNICIAN.next()).isEqualTo(SupportLevel.L2_TEAM_LEAD);
        assertThat(SupportLevel.L3_SYSTEM_ADMIN.next()).isEqualTo(SupportLevel.L4_IT_MANAGER);
        assertThat(SupportLevel.L4_IT_MANAGER.next()).isNull();
        assertThat(SupportLevel.L2_TEAM_LEAD.isAtLeast(SupportLevel.L1_TECHNICIAN)).isTrue();
    }
}

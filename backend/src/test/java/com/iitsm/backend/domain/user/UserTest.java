package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.exception.InvalidCredentialsException;
import com.iitsm.backend.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules shared by every user type, tested through {@link Employee}. */
class UserTest {

    private final PasswordHasher hasher = new FakePasswordHasher();

    private Employee newEmployee(String email, String password) {
        return new Employee("Asha Rao", email, password, hasher, "Finance", "Hyderabad");
    }

    @Test
    @DisplayName("Email is trimmed and stored in lower case")
    void emailIsNormalized() {
        Employee employee = newEmployee("  Asha.Rao@Example.COM ", "secret123");

        assertThat(employee.getEmail()).isEqualTo("asha.rao@example.com");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"not-an-email", "missing@tld", "two@@example.com", "spaces in@example.com", "   "})
    @DisplayName("Malformed email addresses are rejected")
    void invalidEmailRejected(String email) {
        assertThatThrownBy(() -> newEmployee(email, "secret123"))
                .isInstanceOf(InvalidInputException.class);
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"short1", "onlyletters", "12345678"})
    @DisplayName("Weak passwords are rejected (too short, no digit, no letter)")
    void weakPasswordRejected(String password) {
        assertThatThrownBy(() -> newEmployee("asha@example.com", password))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Password length boundaries: 8 accepted, 72 accepted, 73 rejected")
    void passwordLengthBoundaries() {
        String eight = "abcdefg1";
        String seventyTwo = "a".repeat(71) + "1";
        String seventyThree = "a".repeat(72) + "1";

        newEmployee("a@example.com", eight);
        newEmployee("b@example.com", seventyTwo);
        assertThatThrownBy(() -> newEmployee("c@example.com", seventyThree))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Password is stored hashed and can be verified")
    void passwordIsHashedAndVerifiable() {
        Employee employee = newEmployee("asha@example.com", "secret123");

        assertThat(employee.passwordMatches("secret123", hasher)).isTrue();
        assertThat(employee.passwordMatches("wrong-password1", hasher)).isFalse();
        assertThat(employee.passwordMatches(null, hasher)).isFalse();
    }

    @Test
    @DisplayName("Changing password requires the correct current password")
    void changePasswordRequiresCurrentPassword() {
        Employee employee = newEmployee("asha@example.com", "secret123");

        assertThatThrownBy(() -> employee.changePassword("wrong1234", "newSecret456", hasher))
                .isInstanceOf(InvalidCredentialsException.class);

        employee.changePassword("secret123", "newSecret456", hasher);
        assertThat(employee.passwordMatches("newSecret456", hasher)).isTrue();
        assertThat(employee.passwordMatches("secret123", hasher)).isFalse();
    }

    @Test
    @DisplayName("toString never exposes the password hash")
    void toStringHidesPassword() {
        Employee employee = newEmployee("asha@example.com", "secret123");

        assertThat(employee.toString()).doesNotContain("secret123").doesNotContain("hashed:");
    }

    @Test
    @DisplayName("Each subclass reports its own role (polymorphism)")
    void eachSubclassHasItsRole() {
        User employee = newEmployee("e@example.com", "secret123");
        User technician = new Technician("Ravi K", "t@example.com", "secret123", hasher,
                SupportLevel.L1_TECHNICIAN, 2, 5);
        User admin = new Administrator("Meera S", "a@example.com", "secret123", hasher);

        assertThat(employee.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(admin.getRole()).isEqualTo(Role.ADMINISTRATOR);
    }

    @Test
    @DisplayName("New users are active; deactivation keeps the user but marks it inactive")
    void deactivation() {
        Employee employee = newEmployee("asha@example.com", "secret123");
        assertThat(employee.isActive()).isTrue();

        employee.deactivate();

        assertThat(employee.isActive()).isFalse();
    }
}

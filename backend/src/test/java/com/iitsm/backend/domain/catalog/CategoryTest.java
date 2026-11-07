package com.iitsm.backend.domain.catalog;

import com.iitsm.backend.common.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {

    @Test
    @DisplayName("Keywords are normalized to lower case and de-duplicated")
    void keywordsNormalized() {
        Category network = new Category("Network", null);

        network.addKeyword("VPN");
        network.addKeyword(" vpn ");
        network.addKeyword("Latency");

        assertThat(network.getKeywords()).containsExactlyInAnyOrder("vpn", "latency");
    }

    @Test
    @DisplayName("A category needs a non-blank name of at most 60 characters")
    void nameValidated() {
        assertThatThrownBy(() -> new Category("  ", null)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new Category("x".repeat(61), null)).isInstanceOf(InvalidInputException.class);
        assertThat(new Category("x".repeat(60), null).getName()).hasSize(60);
    }

    @Test
    @DisplayName("Keywords cannot be modified from outside the category")
    void keywordsReadOnly() {
        Category network = new Category("Network", null);

        assertThatThrownBy(() -> network.getKeywords().add("hack"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Unsaved categories are equal only to themselves")
    void unsavedEquality() {
        Category a = new Category("Network", null);
        Category b = new Category("Network", null);

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
    }
}

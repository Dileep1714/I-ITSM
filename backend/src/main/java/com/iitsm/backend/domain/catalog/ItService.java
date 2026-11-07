package com.iitsm.backend.domain.catalog;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * An IT service the organization runs: Payment, Email, VPN, Customer Database…
 *
 * <p><b>Service dependency graph:</b> each service records the services it depends on (Payment → Database,
 * Payment → Authentication). Incident correlation uses this graph to spot a likely common root cause, and it is
 * one of the spec's Innovation items. Cycles are rejected, because "A depends on B depends on A" makes root-cause
 * analysis meaningless.
 */
@Entity
@Table(name = "it_services")
public class ItService {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessCriticality criticality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    /** Aggregation: dependencies are independent services; removing a link does not delete the service. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
    private Set<ItService> dependsOn = new HashSet<>();

    /** Required by JPA; not for application code. */
    protected ItService() {
    }

    public ItService(String name, String description, BusinessCriticality criticality) {
        this.name = Validate.requireText(name, "Service name", MAX_NAME_LENGTH);
        this.description = Validate.optionalText(description, "Service description", MAX_DESCRIPTION_LENGTH);
        this.criticality = Validate.requireNonNull(criticality, "Business criticality");
        this.status = ServiceStatus.OPERATIONAL;
    }

    // ----- behaviour ---------------------------------------------------------------------------------------------

    public void rename(String newName) {
        this.name = Validate.requireText(newName, "Service name", MAX_NAME_LENGTH);
    }

    public void describe(String newDescription) {
        this.description = Validate.optionalText(newDescription, "Service description", MAX_DESCRIPTION_LENGTH);
    }

    public void changeCriticality(BusinessCriticality newCriticality) {
        this.criticality = Validate.requireNonNull(newCriticality, "Business criticality");
    }

    public void changeStatus(ServiceStatus newStatus) {
        this.status = Validate.requireNonNull(newStatus, "Service status");
    }

    /**
     * Records that this service depends on {@code other}.
     *
     * @throws BusinessRuleException if the link would create a cycle (including a service depending on itself)
     */
    public void addDependency(ItService other) {
        Validate.requireNonNull(other, "Dependency");
        if (this.equals(other) || other.dependsOnTransitively(this)) {
            throw new BusinessRuleException("CIRCULAR_DEPENDENCY",
                    "'" + name + "' cannot depend on '" + other.getName() + "': that would create a dependency cycle");
        }
        dependsOn.add(other);
    }

    public void removeDependency(ItService other) {
        dependsOn.remove(other);
    }

    /** True if this service depends on {@code target} directly or through a chain (A → B → C). */
    public boolean dependsOnTransitively(ItService target) {
        Deque<ItService> toVisit = new ArrayDeque<>(getDependsOn());
        Set<ItService> visited = new HashSet<>();
        while (!toVisit.isEmpty()) {
            ItService current = toVisit.pop();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                toVisit.addAll(current.getDependsOn()); // getter, not field: works with Hibernate lazy proxies
            }
        }
        return false;
    }

    // ----- read access -------------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BusinessCriticality getCriticality() {
        return criticality;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public Set<ItService> getDependsOn() {
        return Collections.unmodifiableSet(dependsOn);
    }

    // ----- identity ----------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ItService that && getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ItService.class.hashCode();
    }

    @Override
    public String toString() {
        return "ItService{id=" + id + ", name='" + name + "'}";
    }
}

package com.iitsm.backend.api.dto;

import com.iitsm.backend.api.dto.IncidentDtos.NamedRef;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.catalog.ServiceStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.Skill;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Administrator management of the catalogue (categories, services), SLA policies and user accounts. Everything
 * the spec says must be configurable "without code changes".
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ----- categories --------------------------------------------------------------------------------------------

    public record CategoryRequest(@NotBlank @Size(max = Category.MAX_NAME_LENGTH) String name,
                                  @Size(max = Category.MAX_DESCRIPTION_LENGTH) String description,
                                  @Size(max = 50) List<@NotBlank @Size(max = Category.MAX_KEYWORD_LENGTH) String> keywords,
                                  Boolean active) {
    }

    public record CategoryAdminView(Long id, String name, String description, boolean active, List<String> keywords) {

        public static CategoryAdminView of(Category category) {
            return new CategoryAdminView(category.getId(), category.getName(), category.getDescription(),
                    category.isActive(), category.getKeywords().stream().sorted().toList());
        }
    }

    // ----- services ----------------------------------------------------------------------------------------------

    public record ServiceRequest(@NotBlank @Size(max = ItService.MAX_NAME_LENGTH) String name,
                                 @Size(max = ItService.MAX_DESCRIPTION_LENGTH) String description,
                                 @NotNull BusinessCriticality criticality,
                                 ServiceStatus status,
                                 List<@NotNull Long> dependsOnIds) {
    }

    public record ServiceAdminView(Long id, String name, String description, BusinessCriticality criticality,
                                   ServiceStatus status, List<NamedRef> dependsOn) {

        public static ServiceAdminView of(ItService service) {
            return new ServiceAdminView(service.getId(), service.getName(), service.getDescription(),
                    service.getCriticality(), service.getStatus(),
                    service.getDependsOn().stream()
                            .sorted(Comparator.comparing(ItService::getName))
                            .map(s -> new NamedRef(s.getId(), s.getName()))
                            .toList());
        }
    }

    // ----- SLA policies ------------------------------------------------------------------------------------------

    /** {@code serviceId} null = the generic policy for this priority. Priority and service are fixed after creation. */
    public record SlaPolicyRequest(@NotNull PriorityLevel priority,
                                   Long serviceId,
                                   @NotNull @Min(1) Long responseMinutes,
                                   @NotNull @Min(1) Long resolutionMinutes,
                                   @NotNull @DecimalMin("0.5") @DecimalMax("0.95") Double warningThreshold) {
    }

    public record SlaPolicyView(Long id, PriorityLevel priority, Long serviceId, String serviceName,
                                long responseMinutes, long resolutionMinutes, double warningThreshold) {

        public static SlaPolicyView of(SlaPolicy policy) {
            ItService service = policy.getService();
            return new SlaPolicyView(policy.getId(), policy.getPriority(),
                    service != null ? service.getId() : null, service != null ? service.getName() : null,
                    policy.getResponseTarget().toMinutes(), policy.getResolutionTarget().toMinutes(),
                    policy.getWarningThreshold());
        }
    }

    // ----- users -------------------------------------------------------------------------------------------------

    public record SkillRequest(@NotNull Long categoryId, @NotNull @Min(Skill.MIN_PROFICIENCY) @Max(Skill.MAX_PROFICIENCY) Integer proficiency) {
    }

    public record SkillView(Long categoryId, String categoryName, int proficiency) {
    }

    /**
     * Creates any kind of account. Technician fields are required only for {@code TECHNICIAN}; employee fields are
     * used only for {@code EMPLOYEE}.
     */
    public record CreateUserRequest(@NotNull Role role,
                                    @NotBlank @Size(max = User.MAX_NAME_LENGTH) String fullName,
                                    @NotBlank @Email @Size(max = User.MAX_EMAIL_LENGTH) String email,
                                    @NotBlank @Size(min = User.MIN_PASSWORD_LENGTH, max = User.MAX_PASSWORD_LENGTH) String password,
                                    @Size(max = Employee.MAX_DETAIL_LENGTH) String department,
                                    @Size(max = Employee.MAX_DETAIL_LENGTH) String location,
                                    SupportLevel supportLevel,
                                    @Min(0) @Max(Technician.MAX_EXPERIENCE_YEARS) Integer experienceYears,
                                    @Min(Technician.MIN_CAPACITY) @Max(Technician.MAX_CAPACITY) Integer maxActiveIncidents,
                                    List<@Valid SkillRequest> skills) {

        @Override
        public String toString() {
            return "CreateUserRequest[role=" + role + ", email=" + email + "]";
        }
    }

    /** Profile and account status. Every field is optional: only what is given changes. */
    public record UpdateUserRequest(@Size(max = User.MAX_NAME_LENGTH) String fullName,
                                    @Email @Size(max = User.MAX_EMAIL_LENGTH) String email,
                                    Boolean active,
                                    @Size(max = Employee.MAX_DETAIL_LENGTH) String department,
                                    @Size(max = Employee.MAX_DETAIL_LENGTH) String location) {
    }

    /** Technician-specific settings. Every field is optional; {@code skills}, when given, replaces all skills. */
    public record UpdateTechnicianRequest(SupportLevel supportLevel,
                                          @Min(0) @Max(Technician.MAX_EXPERIENCE_YEARS) Integer experienceYears,
                                          @Min(Technician.MIN_CAPACITY) @Max(Technician.MAX_CAPACITY) Integer maxActiveIncidents,
                                          Availability availability,
                                          List<@Valid SkillRequest> skills) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = User.MIN_PASSWORD_LENGTH, max = User.MAX_PASSWORD_LENGTH) String password) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    public record AvailabilityRequest(@NotNull Availability availability) {
    }

    public record UserAdminView(Long id, String fullName, String email, Role role, boolean active, Instant createdAt,
                                String department, String location,
                                SupportLevel supportLevel, Integer experienceYears, Integer maxActiveIncidents,
                                Availability availability, List<SkillView> skills, Integer activeIncidents) {

        public static UserAdminView of(User user, int activeIncidents) {
            String department = null;
            String location = null;
            SupportLevel level = null;
            Integer experience = null;
            Integer capacity = null;
            Availability availability = null;
            List<SkillView> skills = List.of();
            Integer active = null;
            if (user instanceof Employee employee) {
                department = employee.getDepartment();
                location = employee.getLocation();
            } else if (user instanceof Technician technician) {
                level = technician.getSupportLevel();
                experience = technician.getExperienceYears();
                capacity = technician.getMaxActiveIncidents();
                availability = technician.getAvailability();
                skills = technician.getSkills().stream()
                        .map(s -> new SkillView(s.getCategory().getId(), s.getCategory().getName(), s.getProficiency()))
                        .sorted(Comparator.comparing(SkillView::categoryName))
                        .toList();
                active = activeIncidents;
            }
            return new UserAdminView(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                    user.isActive(), user.getCreatedAt(), department, location, level, experience, capacity,
                    availability, skills, active);
        }
    }
}

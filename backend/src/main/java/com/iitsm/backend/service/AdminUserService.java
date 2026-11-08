package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.AdminDtos.CreateUserRequest;
import com.iitsm.backend.api.dto.AdminDtos.SkillRequest;
import com.iitsm.backend.api.dto.AdminDtos.UpdateTechnicianRequest;
import com.iitsm.backend.api.dto.AdminDtos.UpdateUserRequest;
import com.iitsm.backend.api.dto.AdminDtos.UserAdminView;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.UserRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrator management of accounts: create employees, technicians and administrators; edit profiles;
 * activate/deactivate; reset passwords; set technician level, capacity, availability and skills.
 *
 * <p>Uses the <b>Factory</b> idea for users: {@link #newUser} is the single place that knows which subclass to
 * build for a role.
 */
@Service
@Transactional
public class AdminUserService {

    private final UserRepository users;
    private final CategoryRepository categories;
    private final PasswordHasher hasher;
    private final AssignmentCandidateService workload;
    private final CurrentUserService currentUser;

    public AdminUserService(UserRepository users, CategoryRepository categories, PasswordHasher hasher,
                            AssignmentCandidateService workload, CurrentUserService currentUser) {
        this.users = users;
        this.categories = categories;
        this.hasher = hasher;
        this.workload = workload;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<UserAdminView> list(Role role) {
        Map<Long, Long> active = workload.workloadByTechnician();
        return users.findAll(Sort.by("fullName")).stream()
                .filter(user -> role == null || user.getRole() == role)
                .map(user -> view(user, active))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAdminView get(Long id) {
        return view(load(id), workload.workloadByTechnician());
    }

    public UserAdminView create(CreateUserRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        User user = users.save(newUser(request, email));
        if (user instanceof Technician technician && request.skills() != null) {
            replaceSkills(technician, request.skills());
        }
        return view(user, Map.of());
    }

    /** Factory method: the right {@link User} subclass for the requested role. */
    private User newUser(CreateUserRequest r, String email) {
        return switch (r.role()) {
            case EMPLOYEE -> new Employee(r.fullName(), email, r.password(), hasher, r.department(), r.location());
            case ADMINISTRATOR -> new Administrator(r.fullName(), email, r.password(), hasher);
            case TECHNICIAN -> {
                if (r.supportLevel() == null || r.maxActiveIncidents() == null) {
                    throw new InvalidInputException("A technician needs a support level and a maximum number of active incidents");
                }
                yield new Technician(r.fullName(), email, r.password(), hasher, r.supportLevel(),
                        r.experienceYears() != null ? r.experienceYears() : 0, r.maxActiveIncidents());
            }
        };
    }

    public UserAdminView update(Long id, UpdateUserRequest request) {
        User user = load(id);
        if (request.fullName() != null) {
            user.rename(request.fullName());
        }
        if (request.email() != null) {
            String email = request.email().strip().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
                throw new BusinessRuleException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
            }
            user.changeEmail(email);
        }
        if (user instanceof Employee employee && (request.department() != null || request.location() != null)) {
            employee.updateWorkDetails(
                    request.department() != null ? request.department() : employee.getDepartment(),
                    request.location() != null ? request.location() : employee.getLocation());
        }
        if (request.active() != null) {
            setActive(user, request.active());
        }
        return view(user, workload.workloadByTechnician());
    }

    /**
     * Deactivated users are locked out at once (every request reloads the user), but stay in the database so
     * incident history keeps its references. An administrator cannot lock themselves out, and the last active
     * administrator cannot be deactivated.
     */
    private void setActive(User user, boolean active) {
        if (active) {
            user.activate();
            return;
        }
        if (user.equals(currentUser.require())) {
            throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
        }
        if (user instanceof Administrator && users.findActiveAdministrators().size() <= 1) {
            throw new BusinessRuleException("LAST_ADMINISTRATOR", "The last active administrator cannot be deactivated");
        }
        user.deactivate();
    }

    public UserAdminView updateTechnician(Long id, UpdateTechnicianRequest request) {
        if (!(load(id) instanceof Technician technician)) {
            throw new BusinessRuleException("NOT_A_TECHNICIAN", "User " + id + " is not a technician");
        }
        if (request.supportLevel() != null) {
            technician.changeSupportLevel(request.supportLevel());
        }
        if (request.experienceYears() != null) {
            technician.updateExperience(request.experienceYears());
        }
        if (request.maxActiveIncidents() != null) {
            technician.changeCapacity(request.maxActiveIncidents());
        }
        if (request.availability() != null) {
            technician.changeAvailability(request.availability());
        }
        if (request.skills() != null) {
            replaceSkills(technician, request.skills());
        }
        return view(technician, workload.workloadByTechnician());
    }

    public void resetPassword(Long id, String newPassword) {
        load(id).resetPassword(newPassword, hasher);
    }

    /** The current user's own profile, including technician details such as availability and skills. */
    @Transactional(readOnly = true)
    public UserAdminView me() {
        return view(currentUser.require(), workload.workloadByTechnician());
    }

    /** A technician sets their own availability (e.g. going off duty). */
    public UserAdminView changeOwnAvailability(Availability availability) {
        if (!(currentUser.require() instanceof Technician technician)) {
            throw new BusinessRuleException("NOT_A_TECHNICIAN", "Only technicians have an availability");
        }
        technician.changeAvailability(availability);
        return view(technician, workload.workloadByTechnician());
    }

    private void replaceSkills(Technician technician, List<SkillRequest> skills) {
        Set<Long> ids = skills.stream().map(SkillRequest::categoryId).collect(Collectors.toSet());
        if (ids.size() != skills.size()) {
            throw new InvalidInputException("Each category can appear only once in the skills");
        }
        Map<Long, Category> byId = new HashMap<>();
        for (Long categoryId : ids) {
            byId.put(categoryId, categories.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category", categoryId)));
        }
        for (var existing : List.copyOf(technician.getSkills())) {
            if (!ids.contains(existing.getCategory().getId())) {
                technician.removeSkill(existing.getCategory());
            }
        }
        for (SkillRequest skill : skills) {
            technician.addOrUpdateSkill(byId.get(skill.categoryId()), skill.proficiency());
        }
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private static UserAdminView view(User user, Map<Long, Long> activeByTechnician) {
        return UserAdminView.of(user, activeByTechnician.getOrDefault(user.getId(), 0L).intValue());
    }
}

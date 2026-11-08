package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.AdminDtos.AvailabilityRequest;
import com.iitsm.backend.api.dto.AdminDtos.CreateUserRequest;
import com.iitsm.backend.api.dto.AdminDtos.ResetPasswordRequest;
import com.iitsm.backend.api.dto.AdminDtos.UpdateTechnicianRequest;
import com.iitsm.backend.api.dto.AdminDtos.UpdateUserRequest;
import com.iitsm.backend.api.dto.AdminDtos.UserAdminView;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User and technician management (admin only, via the {@code /api/admin/**} rule), plus one self-service
 * endpoint for technicians to set their own availability.
 */
@RestController
public class AdminUserController {

    private final AdminUserService userAdmin;

    public AdminUserController(AdminUserService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @GetMapping("/api/admin/users")
    public List<UserAdminView> list(@RequestParam(required = false) Role role) {
        return userAdmin.list(role);
    }

    @GetMapping("/api/admin/users/{id}")
    public UserAdminView get(@PathVariable Long id) {
        return userAdmin.get(id);
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminView create(@Valid @RequestBody CreateUserRequest request) {
        return userAdmin.create(request);
    }

    @PutMapping("/api/admin/users/{id}")
    public UserAdminView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdmin.update(id, request);
    }

    @PutMapping("/api/admin/users/{id}/technician")
    public UserAdminView updateTechnician(@PathVariable Long id, @Valid @RequestBody UpdateTechnicianRequest request) {
        return userAdmin.updateTechnician(id, request);
    }

    @PostMapping("/api/admin/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userAdmin.resetPassword(id, request.password());
    }

    /** The logged-in user's profile (technicians: level, skills, availability, current workload). */
    @GetMapping("/api/me/profile")
    public UserAdminView profile() {
        return userAdmin.me();
    }

    /** A technician changes their own availability (available, busy, off duty, on leave). */
    @PutMapping("/api/me/availability")
    public UserAdminView changeOwnAvailability(@Valid @RequestBody AvailabilityRequest request) {
        return userAdmin.changeOwnAvailability(request.availability());
    }
}

package com.iitsm.backend.domain.user;

import com.iitsm.backend.common.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A member of staff (or customer) who reports incidents and tracks them. */
@Entity
@Table(name = "employees")
public class Employee extends User {

    public static final int MAX_DETAIL_LENGTH = 80;

    @Column(length = MAX_DETAIL_LENGTH)
    private String department;

    @Column(length = MAX_DETAIL_LENGTH)
    private String location;

    /** Required by JPA; not for application code. */
    protected Employee() {
    }

    public Employee(String fullName, String email, String rawPassword, PasswordHasher hasher,
                    String department, String location) {
        super(fullName, email, rawPassword, hasher);
        updateWorkDetails(department, location);
    }

    @Override
    public Role getRole() {
        return Role.EMPLOYEE;
    }

    /** Both values are optional. */
    public void updateWorkDetails(String department, String location) {
        this.department = Validate.optionalText(department, "Department", MAX_DETAIL_LENGTH);
        this.location = Validate.optionalText(location, "Location", MAX_DETAIL_LENGTH);
    }

    public String getDepartment() {
        return department;
    }

    public String getLocation() {
        return location;
    }
}

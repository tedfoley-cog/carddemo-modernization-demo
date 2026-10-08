package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sign-on user (copybook CSUSR01Y, VSAM USRSEC). The legacy record stores an 8-byte clear-text
 * password; the column holds a credential hash so the online modernization can migrate users
 * without ever persisting clear text.
 */
@Entity
@Table(name = "app_user")
public class UserSecurity extends AssignedIdEntity<String> {

    @Id
    @Column(name = "user_id", length = 8)
    private String userId;

    @Column(name = "first_name", length = 20)
    private String firstName;

    @Column(name = "last_name", length = 20)
    private String lastName;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "user_type", length = 1, nullable = false)
    private String userType;

    @Override
    public String getId() { return userId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
}

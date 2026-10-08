package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** CSUSR01Y SEC-USER-DATA; the password is held as a BCrypt hash (ONL-SEC-02). */
@Entity
@Table(name = "user_security")
public class UserSecurity {
    @Id
    @Column(name = "sec_usr_id")
    private String userId;
    @Column(name = "sec_usr_fname")
    private String firstName;
    @Column(name = "sec_usr_lname")
    private String lastName;
    @Column(name = "sec_usr_pwd_hash")
    private String passwordHash;
    @Column(name = "sec_usr_type")
    private String userType;

    public String getUserId() { return userId; }
    public void setUserId(String v) { userId = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public String getUserType() { return userType; }
    public void setUserType(String v) { userType = v; }
    public boolean isAdmin() { return "A".equals(userType); }
}

package com.carddemo.online.config;

/** Authenticated principal: COMMAREA CDEMO-USER-ID + CDEMO-USER-TYPE ('A' admin, 'U' user). */
public record SessionUser(String userId, String userType, String displayName) {
    public boolean isAdmin() {
        return "A".equals(userType);
    }
}

package com.faust.easychat.service;

public interface TokenBlacklistService {

    void blacklist(String token, long remainingMs);

    boolean isBlacklisted(String token);

    /** 改密码后，作废该用户此刻之前签发的 access / refresh token。 */
    void revokeUserTokens(String userId);

    boolean isRevokedByPasswordChange(String userId, java.util.Date issuedAt);
}

package com.hrm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_recovery")
public class PasswordRecovery {
    @Id @Column(name = "account_id") public Long accountId;
    @Column(name = "code_hash", nullable = false, length = 64) public String codeHash;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "sent_at", nullable = false) public Instant sentAt;
    @Column(nullable = false) public int attempts;
    @Column(name = "token_version", nullable = false) public int tokenVersion;
    protected PasswordRecovery() {}
    public PasswordRecovery(Long accountId) { this.accountId = accountId; }
}

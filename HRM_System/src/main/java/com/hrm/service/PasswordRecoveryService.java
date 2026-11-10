package com.hrm.service;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.model.AuditLog;
import com.hrm.model.PasswordRecovery;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.AuditLogRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PasswordRecoveryRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final PasswordRecoveryRepository recoveries;
    private final AuditLogRepository audit;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final String secret;
    private final String from;

    public PasswordRecoveryService(AccountRepository accounts, EmployeeRepository employees,
            PasswordRecoveryRepository recoveries, AuditLogRepository audit, PasswordEncoder encoder,
            ObjectProvider<JavaMailSender> mail, @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.recovery.mail-from:}") String from) {
        this.accounts = accounts; this.employees = employees; this.recoveries = recoveries;
        this.audit = audit; this.encoder = encoder; this.mail = mail; this.secret = secret; this.from = from;
    }

    @Transactional
    public void request(String username) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ email chưa được cấu hình");
        }
        Account account = accounts.findByUsernameForRecovery(username).orElse(null);
        if (!eligible(account)) return;
        String email = employees.findById(account.getEmployeeId()).map(e -> e.getEmail()).orElse(null);
        if (email == null || email.isBlank()) return;
        Instant now = Instant.now();
        PasswordRecovery recovery = recoveries.findById(account.getAccountId()).orElse(null);
        if (recovery != null && recovery.sentAt.plusSeconds(60).isAfter(now)) return;
        String code = String.format(java.util.Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Mã khôi phục mật khẩu HRM Center");
        message.setText("Mã khôi phục mật khẩu của bạn là: " + code + "\nMã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        try { sender.send(message); }
        catch (org.springframework.mail.MailException ex) {
            log.warn("Không thể gửi email khôi phục mật khẩu: {}", ex.getClass().getSimpleName());
            return;
        }
        if (recovery == null) recovery = new PasswordRecovery(account.getAccountId());
        recovery.codeHash = hash(account.getAccountId(), code);
        recovery.expiresAt = now.plusSeconds(600); recovery.sentAt = now;
        recovery.attempts = 0; recovery.tokenVersion = account.getTokenVersion();
        recoveries.save(recovery);
    }

    @Transactional
    public boolean verify(String username, String code) {
        Account account = accounts.findByUsernameForRecovery(username).orElse(null);
        return check(account, code) != null;
    }

    @Transactional
    public boolean reset(String username, String code, String password, String confirmation) {
        if (password.length() < 8 || password.length() > 72 || password.getBytes(StandardCharsets.UTF_8).length > 72 || !password.equals(confirmation)) {
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 72 ký tự, không quá 72 byte UTF-8 và khớp với xác nhận");
        }
        Account account = accounts.findByUsernameForRecovery(username).orElse(null);
        PasswordRecovery recovery = check(account, code);
        if (recovery == null) return false;
        Long accountId = account.getAccountId();
        recoveries.delete(recovery);
        recoveries.flush();
        if (accounts.updatePasswordAndIncrementTokenVersion(accountId, encoder.encode(password)) != 1)
            throw new IllegalStateException("Không thể cập nhật mật khẩu");
        audit.save(new AuditLog(accountId, "PASSWORD_RECOVERED", "accounts", accountId, "Password recovery"));
        return true;
    }

    private PasswordRecovery check(Account account, String code) {
        if (!eligible(account)) return null;
        PasswordRecovery recovery = recoveries.findById(account.getAccountId()).orElse(null);
        if (recovery == null || recovery.attempts >= 5 || !recovery.expiresAt.isAfter(Instant.now())
                || recovery.tokenVersion != account.getTokenVersion()) return null;
        if (!MessageDigest.isEqual(recovery.codeHash.getBytes(StandardCharsets.US_ASCII), hash(account.getAccountId(), code).getBytes(StandardCharsets.US_ASCII))) {
            recovery.attempts++;
            recoveries.save(recovery);
            return null;
        }
        return recovery;
    }

    private boolean eligible(Account account) {
        return account != null && account.isActive() && employees.existsByEmployeeIdAndEmploymentStatus(account.getEmployeeId(), EmploymentStatus.DANG_LAM);
    }

    private String hash(Long accountId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((accountId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}

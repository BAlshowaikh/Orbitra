/*
  AuthService.java
  Business logic for registration and login - the only place that decides who's
  allowed to become an account and who's allowed to authenticate as one.
  Controllers stay thin and just delegate here.
*/
package com.orbitra.auth_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.auth_service.dto.AuthResponse;
import com.orbitra.auth_service.dto.LoginRequest;
import com.orbitra.auth_service.dto.RegisterRequest;
import com.orbitra.auth_service.exception.DuplicateEmailException;
import com.orbitra.auth_service.model.Account;
import com.orbitra.auth_service.model.Role;
import com.orbitra.auth_service.repository.AccountRepository;
import com.orbitra.auth_service.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Never log the raw password or the JWT itself - only identifiers like
    // email/account id, which are safe to see in server logs.
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Constructor injection: Spring supplies the beans defined in SecurityConfig
    // (PasswordEncoder) and JwtService automatically - no `new` needed here.
    public AuthService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ---------------- METHOD 1: Register ----------------
    // @Transactional: the existsByEmail check and the save() below happen as one
    // atomic unit - if anything after the check fails, the account isn't left
    // half-persisted. (Doesn't fully close the duplicate-email race by itself;
    // that's what the DB's unique constraint on email is the final guard for.)
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Bootstrap-only: self-registration as ADMIN is allowed exactly once, to
        // create the platform's first admin account. Once any ADMIN account
        // exists, this path closes itself - further admins must be promoted by
        // an existing admin instead (not yet built). Same check-then-act shape
        // as the duplicate-email check below; the race is acceptable here since
        // the worst case is a second self-registered admin, not an attacker
        // granted admin over an arbitrary target account.
        if (request.role() == Role.ADMIN && accountRepository.existsByRole(Role.ADMIN)) {
            log.warn("Rejected registration attempt for email={} - an ADMIN account already exists, self-registration as ADMIN is closed", request.email());
            throw new IllegalArgumentException("Cannot self-register as ADMIN - an admin account already exists");
        }

        if (accountRepository.existsByEmail(request.email())) {
            log.warn("Rejected registration attempt for email={} - email already registered", request.email());
            throw new DuplicateEmailException("Email already registered");
        }

        // Cross-field rule noted in RegisterRequest: partnerType is required for
        // PARTNER accounts and meaningless for anyone else.
        if (request.role() == Role.PARTNER && request.partnerType() == null) {
            log.warn("Rejected registration attempt for email={} - partnerType missing for PARTNER role", request.email());
            throw new IllegalArgumentException("partnerType is required when role is PARTNER");
        }

        Account account = Account.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .partnerType(request.role() == Role.PARTNER ? request.partnerType() : null)
                .enabled(true)
                .build();

        accountRepository.save(account);
        log.info("Registered new account id={} email={} role={}", account.getId(), account.getEmail(), account.getRole());

        return buildAuthResponse(account);
    }

    // ---------------- METHOD 2: Login ----------------
    // Read-only - no @Transactional needed since nothing is written.
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for email={} - no account with that email", request.email());
                    return new IllegalArgumentException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            log.warn("Failed login attempt for email={} - password mismatch", request.email());
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!account.isEnabled()) {
            log.warn("Rejected login attempt for email={} - account is disabled", request.email());
            throw new IllegalArgumentException("Account is disabled");
        }

        log.info("Successful login for account id={} email={}", account.getId(), account.getEmail());
        return buildAuthResponse(account);
    }

    // ---------------- METHOD 3: Build AuthResponse ----------------
    private AuthResponse buildAuthResponse(Account account) {
        String token = jwtService.generateToken(account);
        return new AuthResponse(token, jwtService.getExpirationMs(), account.getRole());
    }
}

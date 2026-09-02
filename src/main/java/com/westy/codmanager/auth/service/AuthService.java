package com.westy.codmanager.auth.service;

import com.westy.codmanager.auth.domain.Role;
import com.westy.codmanager.auth.domain.User;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.auth.web.AuthDtos.LoginRequest;
import com.westy.codmanager.auth.web.AuthDtos.RegisterRequest;
import com.westy.codmanager.auth.web.AuthDtos.TokenResponse;
import com.westy.codmanager.common.exception.BusinessRuleException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration and login for shop owners.
 *
 * Both endpoints return a token rather than a session, because the API is
 * stateless and any instance must be able to serve any request.
 *
 * Login deliberately gives the same answer for an unknown email and a wrong
 * password, and verifies the hash even when the account is already known to be
 * inactive. Distinguishing the cases — in the message or in the response time —
 * would let anyone enumerate which emails have accounts here.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalise(request.email());

        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "That email is already registered");
        }

        User user = new User(email, encoder.encode(request.password()),
                request.storeName().trim(), Role.OWNER);

        users.save(user);

        return TokenResponse.bearer(jwt.issue(user), jwt.ttlSeconds());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        /*
         * The password is verified even when the account is inactive, so the
         * response time does not reveal which accounts exist.
         */
        boolean passwordMatches = encoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches || !user.isActive()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return TokenResponse.bearer(jwt.issue(user), jwt.ttlSeconds());
    }

    private String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

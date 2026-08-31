package com.westy.codmanager.auth.web;

import com.westy.codmanager.auth.domain.User;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.auth.service.AuthService;
import com.westy.codmanager.auth.web.AuthDtos.LoginRequest;
import com.westy.codmanager.auth.web.AuthDtos.RegisterRequest;
import com.westy.codmanager.auth.web.AuthDtos.TokenResponse;
import com.westy.codmanager.auth.web.AuthDtos.UserResponse;
import com.westy.codmanager.common.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a seller account and return an access token")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for an access token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the account behind the current token")
    public UserResponse me(@AuthenticationPrincipal String userId) {
        User user = users.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("User", userId));

        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getStoreName(),
                user.getRole().name());
    }
}

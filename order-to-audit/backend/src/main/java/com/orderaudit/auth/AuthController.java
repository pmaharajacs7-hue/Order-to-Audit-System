package com.orderaudit.auth;

import com.orderaudit.security.JwtService;
import com.orderaudit.user.User;
import com.orderaudit.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, String fullName, String role) {}

    public record MeResponse(String username, String fullName, String role) {}

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserRepository users;

    public AuthController(AuthenticationManager authManager, JwtService jwt, UserRepository users) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.users = users;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        // Throws BadCredentialsException / DisabledException -> mapped to 401
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        User user = users.findByUsername(req.username()).orElseThrow();
        String role = user.getRole().getName().name();
        return new LoginResponse(jwt.generate(user.getUsername(), role),
                user.getUsername(), user.getFullName(), role);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        User user = users.findByUsername(auth.getName()).orElseThrow();
        return new MeResponse(user.getUsername(), user.getFullName(), user.getRole().getName().name());
    }
}

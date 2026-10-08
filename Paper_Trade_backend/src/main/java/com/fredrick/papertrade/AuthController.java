package com.fredrick.papertrade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    record SignupRequest(@NotBlank String name, @Email @NotBlank String email, @Size(min = 6) String password) {}
    record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    record UserResponse(Long id, String name, String email, double balance) {}
    record AuthResponse(String token, UserResponse user) {}

    private final UserRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = users.save(new User(req.name().trim(), email, encoder.encode(req.password())));
        return toAuth(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toAuth(user);
    }

    // Used by the app on startup to check a saved token and fetch the current user
    @GetMapping("/me")
    public UserResponse me(@RequestHeader(value = "Authorization", defaultValue = "") String header) {
        try {
            Long id = jwt.readUserId(header.replaceFirst("^Bearer ", ""));
            return toResponse(users.findById(id).orElseThrow());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
        }
    }

    private AuthResponse toAuth(User u) {
        return new AuthResponse(jwt.createToken(u.getId()), toResponse(u));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getBalance());
    }
}

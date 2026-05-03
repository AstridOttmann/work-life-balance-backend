# JWT Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add JWT-based multi-user authentication to the Spring Boot backend so every user sees and can only edit their own data.

**Architecture:** Stateless JWT auth via Spring Security. A `User` entity owns all `DailyEntry` records (and transitively all `Appointment` and `TimeBlock` records). Every protected endpoint extracts the authenticated user from Spring Security's context via `@AuthenticationPrincipal` and passes it into the service layer. The `/api/auth` endpoints (register/login) are public; everything else requires a valid Bearer token.

**Tech Stack:** Spring Boot 3.3.0, Spring Security 6, JJWT 0.12.3, BCrypt, PostgreSQL 16, JUnit 5, H2 (test only)

---

### Task 1: Add dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Spring Security, JJWT, spring-security-test, H2 to pom.xml**

Inside the `<dependencies>` block, after the existing `spring-boot-starter-validation` dependency, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify Maven resolves dependencies**

Run: `./mvnw dependency:resolve -q`
Expected: `BUILD SUCCESS`

Note: Adding Spring Security without a `SecurityConfig` will temporarily lock all endpoints behind HTTP Basic auth. This is resolved in Task 5.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add Spring Security, JJWT, and H2 test dependencies"
```

---

### Task 2: User model and UserRepository

**Files:**
- Create: `src/main/java/com/worklifebalance/model/User.java`
- Create: `src/main/java/com/worklifebalance/repository/UserRepository.java`

- [ ] **Step 1: Create User.java**

```java
package com.worklifebalance.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Step 2: Create UserRepository.java**

```java
package com.worklifebalance.repository;

import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worklifebalance/model/User.java \
        src/main/java/com/worklifebalance/repository/UserRepository.java
git commit -m "feat: add User model and UserRepository"
```

---

### Task 3: JWT infrastructure

**Files:**
- Create: `src/main/java/com/worklifebalance/security/JwtUtil.java`
- Create: `src/main/java/com/worklifebalance/security/JwtAuthFilter.java`

- [ ] **Step 1: Create JwtUtil.java**

```java
package com.worklifebalance.security;

import com.worklifebalance.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key())
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Create JwtAuthFilter.java**

```java
package com.worklifebalance.security;

import com.worklifebalance.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        if (jwtUtil.isValid(token)) {
            String email = jwtUtil.extractEmail(token);
            userRepository.findByEmail(email).ifPresent(user -> {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worklifebalance/security/
git commit -m "feat: add JwtUtil and JwtAuthFilter"
```

---

### Task 4: Auth DTOs and AuthService

**Files:**
- Create: `src/main/java/com/worklifebalance/dto/RegisterRequest.java`
- Create: `src/main/java/com/worklifebalance/dto/LoginRequest.java`
- Create: `src/main/java/com/worklifebalance/dto/AuthResponse.java`
- Create: `src/main/java/com/worklifebalance/service/AuthService.java`

- [ ] **Step 1: Create RegisterRequest.java**

```java
package com.worklifebalance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email @NotBlank
    private String email;

    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

- [ ] **Step 2: Create LoginRequest.java**

```java
package com.worklifebalance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @Email @NotBlank
    private String email;

    @NotBlank
    private String password;
}
```

- [ ] **Step 3: Create AuthResponse.java**

```java
package com.worklifebalance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
}
```

- [ ] **Step 4: Create AuthService.java**

```java
package com.worklifebalance.service;

import com.worklifebalance.dto.AuthResponse;
import com.worklifebalance.dto.LoginRequest;
import com.worklifebalance.dto.RegisterRequest;
import com.worklifebalance.model.User;
import com.worklifebalance.repository.UserRepository;
import com.worklifebalance.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtUtil.generateToken(user), user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(jwtUtil.generateToken(user), user.getEmail());
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worklifebalance/dto/RegisterRequest.java \
        src/main/java/com/worklifebalance/dto/LoginRequest.java \
        src/main/java/com/worklifebalance/dto/AuthResponse.java \
        src/main/java/com/worklifebalance/service/AuthService.java
git commit -m "feat: add auth DTOs and AuthService"
```

---

### Task 5: SecurityConfig, AuthController, and properties

**Files:**
- Create: `src/main/java/com/worklifebalance/config/SecurityConfig.java`
- Create: `src/main/java/com/worklifebalance/controller/AuthController.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/test/resources/application-test.properties`

- [ ] **Step 1: Add JWT config to application.properties**

Append to `src/main/resources/application.properties`:

```properties
jwt.secret=${JWT_SECRET:dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItZGV2ZWxvcG1lbnQ=}
jwt.expiration-ms=86400000
```

The fallback value is for local development only. For production set the `JWT_SECRET` env var.
Generate a production secret with: `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` (PowerShell) or `openssl rand -base64 32` (bash).

- [ ] **Step 2: Create src/test/resources/application-test.properties**

```properties
spring.datasource.url=jdbc:h2:mem:testdb;NON_KEYWORDS=VALUE
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=
jwt.expiration-ms=3600000
```

- [ ] **Step 3: Create SecurityConfig.java**

```java
package com.worklifebalance.config;

import com.worklifebalance.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configure(http))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

- [ ] **Step 4: Create AuthController.java**

```java
package com.worklifebalance.controller;

import com.worklifebalance.dto.AuthResponse;
import com.worklifebalance.dto.LoginRequest;
import com.worklifebalance.dto.RegisterRequest;
import com.worklifebalance.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

- [ ] **Step 5: Drop and recreate the local database**

We are about to add a non-nullable `user_id` column to `daily_entry`. Existing rows will violate the constraint. Clear existing data now:

```bash
docker compose down -v && docker compose up -d
```

Or via psql: `docker exec -it <postgres-container> psql -U wlb -d worklifebalance -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"`

- [ ] **Step 6: Start the app and smoke-test auth**

Run: `./mvnw spring-boot:run`

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' | jq
```
Expected: `{"token":"eyJ...","email":"test@example.com"}`

```bash
# Protected endpoint without token
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/entries
```
Expected: `401`

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/application.properties \
        src/test/resources/application-test.properties \
        src/main/java/com/worklifebalance/config/SecurityConfig.java \
        src/main/java/com/worklifebalance/controller/AuthController.java
git commit -m "feat: add SecurityConfig and AuthController — JWT auth is live"
```

---

### Task 6: Scope DailyEntry to User

**Files:**
- Modify: `src/main/java/com/worklifebalance/model/DailyEntry.java`
- Modify: `src/main/java/com/worklifebalance/repository/DailyEntryRepository.java`
- Modify: `src/main/java/com/worklifebalance/service/EntryService.java`

- [ ] **Step 1: Replace DailyEntry.java**

The `date` unique constraint moves from `@Column(unique=true)` to a composite `@UniqueConstraint(columnNames = {"user_id", "date"})` so the same date can exist for different users.

```java
package com.worklifebalance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "daily_entry",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"})
)
@Getter
@Setter
public class DailyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    private Double workHours;
    private Double freeTimeHours;
    private Double sleepingHours;

    @Column(nullable = false)
    private Double mood;

    private Double health;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "dailyEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Appointment> appointments = new ArrayList<>();

    @OneToMany(mappedBy = "dailyEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeBlock> timeBlocks = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Replace DailyEntryRepository.java**

```java
package com.worklifebalance.repository;

import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyEntryRepository extends JpaRepository<DailyEntry, Long> {
    List<DailyEntry> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate from, LocalDate to);
    List<DailyEntry> findAllByUserOrderByDateDesc(User user);
    Optional<DailyEntry> findByUserAndDate(User user, LocalDate date);
    Optional<DailyEntry> findByIdAndUser(Long id, User user);
}
```

- [ ] **Step 3: Replace EntryService.java**

All public methods now accept a `User user` parameter:

```java
package com.worklifebalance.service;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.SummaryDto;
import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
import com.worklifebalance.repository.DailyEntryRepository;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final DailyEntryRepository repository;

    public List<DailyEntryDto> getAll(User user, LocalDate from, LocalDate to) {
        List<DailyEntry> entries = (from != null && to != null)
                ? repository.findByUserAndDateBetweenOrderByDateAsc(user, from, to)
                : repository.findAllByUserOrderByDateDesc(user);
        return entries.stream().map(this::toDto).toList();
    }

    public DailyEntryDto getById(User user, Long id) {
        return toDto(findOrThrow(user, id));
    }

    public DailyEntryDto create(User user, DailyEntryDto dto) {
        if (repository.findByUserAndDate(user, dto.getDate()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An entry for this date already exists");
        }
        DailyEntry entry = toEntity(dto);
        entry.setUser(user);
        return toDto(repository.save(entry));
    }

    public DailyEntryDto update(User user, Long id, DailyEntryDto dto) {
        DailyEntry entry = findOrThrow(user, id);
        repository.findByUserAndDate(user, dto.getDate()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An entry for this date already exists");
            }
        });
        entry.setDate(dto.getDate());
        entry.setSleepingHours(dto.getSleepingHours());
        entry.setMood(dto.getMood());
        entry.setHealth(dto.getHealth());
        entry.setNotes(dto.getNotes());
        return toDto(repository.save(entry));
    }

    public void delete(User user, Long id) {
        repository.delete(findOrThrow(user, id));
    }

    public SummaryDto getSummary(User user, String period, LocalDate date) {
        LocalDate start;
        LocalDate end;
        if ("monthly".equalsIgnoreCase(period)) {
            start = date.with(TemporalAdjusters.firstDayOfMonth());
            end = date.with(TemporalAdjusters.lastDayOfMonth());
        } else {
            start = date.with(DayOfWeek.MONDAY);
            end = date.with(DayOfWeek.SUNDAY);
        }

        List<DailyEntry> entries = repository.findByUserAndDateBetweenOrderByDateAsc(user, start, end);

        SummaryDto summary = new SummaryDto();
        summary.setPeriod(period);
        summary.setStartDate(start);
        summary.setEndDate(end);
        summary.setEntries(entries.stream().map(this::toDto).toList());

        summary.setTotalWorkHours(entries.stream().mapToDouble(e ->
                e.getTimeBlocks().isEmpty() ? orZero(e.getWorkHours())
                : e.getTimeBlocks().stream().filter(b -> "WORK".equals(b.getType()))
                        .mapToDouble(b -> minutesBetween(b) / 60.0).sum()
        ).sum());
        summary.setTotalFreeTimeHours(entries.stream().mapToDouble(e ->
                e.getTimeBlocks().isEmpty() ? orZero(e.getFreeTimeHours())
                : e.getTimeBlocks().stream().filter(b -> "FREE".equals(b.getType()))
                        .mapToDouble(b -> minutesBetween(b) / 60.0).sum()
        ).sum());
        summary.setTotalSleepingHours(entries.stream()
                .mapToDouble(e -> orZero(e.getSleepingHours())).sum());

        double totalAppHours = entries.stream()
                .flatMap(e -> e.getAppointments().stream())
                .mapToDouble(a -> orZero(a.getDurationHours()))
                .sum();
        summary.setTotalAppointmentHours(totalAppHours);
        summary.setAppointmentCount(entries.stream()
                .mapToInt(e -> e.getAppointments().size()).sum());

        summary.setAvgMood(entries.stream()
                .filter(e -> e.getMood() != null)
                .mapToDouble(DailyEntry::getMood)
                .average().orElse(0));

        summary.setAvgHealth(entries.stream()
                .filter(e -> e.getHealth() != null)
                .mapToDouble(DailyEntry::getHealth)
                .average().orElse(0));

        return summary;
    }

    private DailyEntry findOrThrow(User user, Long id) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    DailyEntryDto toDto(DailyEntry entry) {
        DailyEntryDto dto = new DailyEntryDto();
        dto.setId(entry.getId());
        dto.setDate(entry.getDate());
        boolean hasBlocks = !entry.getTimeBlocks().isEmpty();
        double compWork = entry.getTimeBlocks().stream().filter(b -> "WORK".equals(b.getType()))
                .mapToDouble(b -> minutesBetween(b) / 60.0).sum();
        double compFree = entry.getTimeBlocks().stream().filter(b -> "FREE".equals(b.getType()))
                .mapToDouble(b -> minutesBetween(b) / 60.0).sum();
        dto.setWorkHours(hasBlocks ? (compWork > 0 ? compWork : null) : entry.getWorkHours());
        dto.setFreeTimeHours(hasBlocks ? (compFree > 0 ? compFree : null) : entry.getFreeTimeHours());
        dto.setSleepingHours(entry.getSleepingHours());
        dto.setMood(entry.getMood());
        dto.setHealth(entry.getHealth());
        dto.setNotes(entry.getNotes());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        dto.setTimeBlocks(entry.getTimeBlocks().stream().map(b -> {
            TimeBlockDto tb = new TimeBlockDto();
            tb.setId(b.getId());
            tb.setDailyEntryId(entry.getId());
            tb.setType(b.getType());
            tb.setStartTime(b.getStartTime());
            tb.setEndTime(b.getEndTime());
            return tb;
        }).toList());
        dto.setAppointments(entry.getAppointments().stream().map(a -> {
            AppointmentDto ad = new AppointmentDto();
            ad.setId(a.getId());
            ad.setDailyEntryId(entry.getId());
            ad.setTitle(a.getTitle());
            ad.setTime(a.getTime());
            ad.setDurationHours(a.getDurationHours());
            return ad;
        }).toList());
        return dto;
    }

    private DailyEntry toEntity(DailyEntryDto dto) {
        DailyEntry entry = new DailyEntry();
        entry.setDate(dto.getDate());
        entry.setSleepingHours(dto.getSleepingHours());
        entry.setMood(dto.getMood());
        entry.setHealth(dto.getHealth());
        entry.setNotes(dto.getNotes());
        return entry;
    }

    private double minutesBetween(TimeBlock b) {
        long mins = ChronoUnit.MINUTES.between(b.getStartTime(), b.getEndTime());
        return mins < 0 ? mins + 1440 : mins;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worklifebalance/model/DailyEntry.java \
        src/main/java/com/worklifebalance/repository/DailyEntryRepository.java \
        src/main/java/com/worklifebalance/service/EntryService.java
git commit -m "feat: scope DailyEntry and EntryService to authenticated user"
```

---

### Task 7: Ownership checks for Appointment and TimeBlock

**Files:**
- Modify: `src/main/java/com/worklifebalance/repository/AppointmentRepository.java`
- Modify: `src/main/java/com/worklifebalance/repository/TimeBlockRepository.java`
- Modify: `src/main/java/com/worklifebalance/service/AppointmentService.java`
- Modify: `src/main/java/com/worklifebalance/service/TimeBlockService.java`

- [ ] **Step 1: Replace AppointmentRepository.java**

```java
package com.worklifebalance.repository;

import com.worklifebalance.model.Appointment;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByIdAndDailyEntry_User(Long id, User user);
}
```

- [ ] **Step 2: Replace TimeBlockRepository.java**

```java
package com.worklifebalance.repository;

import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {
    Optional<TimeBlock> findByIdAndDailyEntry_User(Long id, User user);
}
```

- [ ] **Step 3: Replace AppointmentService.java**

```java
package com.worklifebalance.service;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.model.Appointment;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.User;
import com.worklifebalance.repository.AppointmentRepository;
import com.worklifebalance.repository.DailyEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DailyEntryRepository dailyEntryRepository;

    public AppointmentDto create(User user, AppointmentDto dto) {
        DailyEntry entry = dailyEntryRepository.findByIdAndUser(dto.getDailyEntryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily entry not found"));
        return toDto(appointmentRepository.save(toEntity(dto, entry)));
    }

    public AppointmentDto update(User user, Long id, AppointmentDto dto) {
        Appointment appointment = findOrThrow(user, id);
        appointment.setTitle(dto.getTitle());
        appointment.setTime(dto.getTime());
        appointment.setDurationHours(dto.getDurationHours());
        return toDto(appointmentRepository.save(appointment));
    }

    public void delete(User user, Long id) {
        appointmentRepository.delete(findOrThrow(user, id));
    }

    private Appointment findOrThrow(User user, Long id) {
        return appointmentRepository.findByIdAndDailyEntry_User(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private Appointment toEntity(AppointmentDto dto, DailyEntry entry) {
        Appointment a = new Appointment();
        a.setDailyEntry(entry);
        a.setTitle(dto.getTitle());
        a.setTime(dto.getTime());
        a.setDurationHours(dto.getDurationHours());
        return a;
    }

    AppointmentDto toDto(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(a.getId());
        dto.setDailyEntryId(a.getDailyEntry().getId());
        dto.setTitle(a.getTitle());
        dto.setTime(a.getTime());
        dto.setDurationHours(a.getDurationHours());
        return dto;
    }
}
```

- [ ] **Step 4: Replace TimeBlockService.java**

```java
package com.worklifebalance.service;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
import com.worklifebalance.repository.DailyEntryRepository;
import com.worklifebalance.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final TimeBlockRepository timeBlockRepository;
    private final DailyEntryRepository dailyEntryRepository;

    public TimeBlockDto create(User user, TimeBlockDto dto) {
        DailyEntry entry = dailyEntryRepository.findByIdAndUser(dto.getDailyEntryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily entry not found"));
        return toDto(timeBlockRepository.save(toEntity(dto, entry)));
    }

    public TimeBlockDto update(User user, Long id, TimeBlockDto dto) {
        TimeBlock block = findOrThrow(user, id);
        block.setType(dto.getType());
        block.setStartTime(dto.getStartTime());
        block.setEndTime(dto.getEndTime());
        return toDto(timeBlockRepository.save(block));
    }

    public void delete(User user, Long id) {
        timeBlockRepository.delete(findOrThrow(user, id));
    }

    private TimeBlock findOrThrow(User user, Long id) {
        return timeBlockRepository.findByIdAndDailyEntry_User(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time block not found"));
    }

    private TimeBlock toEntity(TimeBlockDto dto, DailyEntry entry) {
        TimeBlock b = new TimeBlock();
        b.setDailyEntry(entry);
        b.setType(dto.getType());
        b.setStartTime(dto.getStartTime());
        b.setEndTime(dto.getEndTime());
        return b;
    }

    TimeBlockDto toDto(TimeBlock b) {
        TimeBlockDto dto = new TimeBlockDto();
        dto.setId(b.getId());
        dto.setDailyEntryId(b.getDailyEntry().getId());
        dto.setType(b.getType());
        dto.setStartTime(b.getStartTime());
        dto.setEndTime(b.getEndTime());
        return dto;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worklifebalance/repository/AppointmentRepository.java \
        src/main/java/com/worklifebalance/repository/TimeBlockRepository.java \
        src/main/java/com/worklifebalance/service/AppointmentService.java \
        src/main/java/com/worklifebalance/service/TimeBlockService.java
git commit -m "feat: enforce ownership on appointments and time blocks"
```

---

### Task 8: Wire @AuthenticationPrincipal into all controllers

**Files:**
- Modify: `src/main/java/com/worklifebalance/controller/EntryController.java`
- Modify: `src/main/java/com/worklifebalance/controller/AppointmentController.java`
- Modify: `src/main/java/com/worklifebalance/controller/TimeBlockController.java`

- [ ] **Step 1: Replace EntryController.java**

```java
package com.worklifebalance.controller;

import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.SummaryDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService service;

    @GetMapping
    public List<DailyEntryDto> getAll(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getAll(user, from, to);
    }

    @GetMapping("/{id}")
    public DailyEntryDto getById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.getById(user, id);
    }

    @GetMapping("/summary")
    public SummaryDto getSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getSummary(user, period, date != null ? date : LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyEntryDto create(@AuthenticationPrincipal User user, @Valid @RequestBody DailyEntryDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public DailyEntryDto update(@AuthenticationPrincipal User user,
                                @PathVariable Long id,
                                @Valid @RequestBody DailyEntryDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
```

- [ ] **Step 2: Replace AppointmentController.java**

```java
package com.worklifebalance.controller;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto create(@AuthenticationPrincipal User user, @Valid @RequestBody AppointmentDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public AppointmentDto update(@AuthenticationPrincipal User user,
                                 @PathVariable Long id,
                                 @Valid @RequestBody AppointmentDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
```

- [ ] **Step 3: Replace TimeBlockController.java**

```java
package com.worklifebalance.controller;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.TimeBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/time-blocks")
@RequiredArgsConstructor
public class TimeBlockController {

    private final TimeBlockService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeBlockDto create(@AuthenticationPrincipal User user, @Valid @RequestBody TimeBlockDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public TimeBlockDto update(@AuthenticationPrincipal User user,
                               @PathVariable Long id,
                               @Valid @RequestBody TimeBlockDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
```

- [ ] **Step 4: Compile check**

Run: `./mvnw compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: End-to-end isolation smoke test**

```bash
# Register Alice and Bob
ALICE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r '.token')

BOB=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","password":"password123"}' | jq -r '.token')

# Alice creates an entry
curl -s -X POST http://localhost:8080/api/entries \
  -H "Authorization: Bearer $ALICE" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-05-03","mood":7.5,"sleepingHours":8}' | jq

# Alice sees her entry, Bob sees nothing
curl -s http://localhost:8080/api/entries -H "Authorization: Bearer $ALICE" | jq
curl -s http://localhost:8080/api/entries -H "Authorization: Bearer $BOB" | jq
```

Expected: Alice gets array with 1 entry, Bob gets `[]`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worklifebalance/controller/
git commit -m "feat: pass authenticated user to all controllers and services"
```

---

### Task 9: Integration tests

**Files:**
- Create: `src/test/java/com/worklifebalance/AuthControllerTest.java`
- Create: `src/test/java/com/worklifebalance/EntryControllerTest.java`

- [ ] **Step 1: Create AuthControllerTest.java**

```java
package com.worklifebalance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklifebalance.dto.LoginRequest;
import com.worklifebalance.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void register_validRequest_returnsTokenAndEmail() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("newuser@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("dup@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        var reg = new RegisterRequest();
        reg.setEmail("login@example.com");
        reg.setPassword("password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        var login = new LoginRequest();
        login.setEmail("login@example.com");
        login.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        var reg = new RegisterRequest();
        reg.setEmail("wrong@example.com");
        reg.setPassword("password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        var login = new LoginRequest();
        login.setEmail("wrong@example.com");
        login.setPassword("badpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Create EntryControllerTest.java**

```java
package com.worklifebalance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EntryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        var req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void getEntries_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEntries_withToken_returnsOwnEntriesOnly() throws Exception {
        String aliceToken = registerAndGetToken("alice@example.com");
        String bobToken = registerAndGetToken("bob@example.com");

        var entry = new DailyEntryDto();
        entry.setDate(LocalDate.of(2026, 1, 1));
        entry.setMood(8.0);
        entry.setSleepingHours(7.5);

        mockMvc.perform(post("/api/entries")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entry)));

        mockMvc.perform(get("/api/entries")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/entries")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
```

- [ ] **Step 3: Run all tests**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: add integration tests for auth and user-scoped entry access"
```

---

### Task 10: Final verification and push

- [ ] **Step 1: Full build and test**

Run: `./mvnw clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: Push branch**

```bash
git push origin feature/auth
```

Expected: branch pushed. GitHub will offer to open a PR to `main`.

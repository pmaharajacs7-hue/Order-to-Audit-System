package com.orderaudit.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ensures every role exists and (optionally) creates one demo user per role,
 * so each dashboard can be tried immediately. Safe to run on every start.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean seedDemoUsers;
    private final String demoPassword;

    public DataSeeder(RoleRepository roles, UserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed.demo-users:true}") boolean seedDemoUsers,
                      @Value("${app.seed.demo-password:Passw0rd!}") String demoPassword) {
        this.roles = roles;
        this.users = users;
        this.encoder = encoder;
        this.seedDemoUsers = seedDemoUsers;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (RoleName name : RoleName.values()) {
            if (roles.findByName(name).isEmpty()) {
                roles.save(new Role(name));
            }
        }
        if (!seedDemoUsers) return;

        Map<String, RoleName> demo = new LinkedHashMap<>();
        demo.put("admin", RoleName.ADMIN);
        demo.put("salesman", RoleName.SALESMAN);
        demo.put("supervisor", RoleName.PRODUCTION_SUPERVISOR);
        demo.put("accountant", RoleName.ACCOUNTANT);
        demo.put("owner", RoleName.OWNER);

        demo.forEach((username, roleName) -> {
            if (!users.existsByUsername(username)) {
                Role role = roles.findByName(roleName).orElseThrow();
                users.save(new User(username, encoder.encode(demoPassword),
                        "Demo " + roleName.name().replace('_', ' ').toLowerCase(),
                        username + "@example.com", role));
            }
        });
    }
}

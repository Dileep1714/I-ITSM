package com.iitsm.backend.config;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.SlaPolicyRepository;
import com.iitsm.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fills an empty database with a realistic organization for development and the demo video.
 *
 * <p>Runs once at startup when {@code iitsm.seed.enabled=true} <em>and</em> there are no users yet, so it never
 * overwrites real data. Disabled in tests.
 *
 * <p><b>All demo accounts use the password {@value #DEMO_PASSWORD}.</b> For local demos only.
 */
@Component
@ConditionalOnProperty(name = "iitsm.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password123";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final SlaPolicyRepository slaPolicies;
    private final PasswordHasher hasher;

    public DemoDataSeeder(UserRepository users, CategoryRepository categories, ItServiceRepository services,
                          SlaPolicyRepository slaPolicies, PasswordHasher hasher) {
        this.users = users;
        this.categories = categories;
        this.services = services;
        this.slaPolicies = slaPolicies;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Demo data not loaded: the database already contains users");
            return;
        }
        Map<String, Category> category = seedCategories();
        seedServicesAndSlas();
        seedPeople(category);
        log.info("Demo data loaded. Log in as admin@iitsm.example.com / {} (see README for all accounts)",
                DEMO_PASSWORD);
    }

    private Map<String, Category> seedCategories() {
        Map<String, String[]> keywords = new LinkedHashMap<>();
        keywords.put("Hardware", new String[]{"laptop", "printer", "monitor", "keyboard", "battery", "screen"});
        keywords.put("Software", new String[]{"install", "update", "crash", "license", "excel", "outlook"});
        keywords.put("Network", new String[]{"wifi", "vpn", "network", "latency", "dns", "internet", "packet loss"});
        keywords.put("Database", new String[]{"database", "query", "sql", "deadlock", "replication", "timeout"});
        keywords.put("Cloud", new String[]{"aws", "azure", "cloud", "kubernetes", "container", "instance"});
        keywords.put("Security", new String[]{"phishing", "malware", "virus", "breach", "suspicious", "ransomware"});
        keywords.put("Authentication", new String[]{"login", "password", "sso", "mfa", "locked out", "otp"});
        keywords.put("Application", new String[]{"payment", "checkout", "error 500", "page", "transaction", "app"});
        keywords.put("Storage", new String[]{"disk", "storage", "quota", "backup", "file share", "drive"});

        Map<String, Category> created = new LinkedHashMap<>();
        keywords.forEach((name, words) -> {
            Category category = new Category(name, name + " incidents");
            for (String word : words) {
                category.addKeyword(word);
            }
            created.put(name, categories.save(category));
        });
        return created;
    }

    private void seedServicesAndSlas() {
        ItService database = services.save(new ItService("Customer Database",
                "Primary PostgreSQL cluster for customer and order data", BusinessCriticality.MISSION_CRITICAL));
        ItService auth = services.save(new ItService("Authentication",
                "Single sign-on and multi-factor authentication", BusinessCriticality.HIGH));
        ItService storage = services.save(new ItService("Cloud Storage",
                "Shared file storage and backups", BusinessCriticality.HIGH));

        ItService payment = new ItService("Payment",
                "Online payment processing and checkout", BusinessCriticality.MISSION_CRITICAL);
        payment.addDependency(database);
        payment.addDependency(auth);
        services.save(payment);

        ItService email = new ItService("Email", "Corporate email and calendar", BusinessCriticality.MEDIUM);
        email.addDependency(auth);
        services.save(email);

        ItService vpn = new ItService("VPN", "Remote access for staff", BusinessCriticality.HIGH);
        vpn.addDependency(auth);
        services.save(vpn);

        ItService hr = new ItService("HR Portal", "Leave, payroll and HR requests", BusinessCriticality.LOW);
        hr.addDependency(auth);
        hr.addDependency(database);
        services.save(hr);

        ItService intranet = new ItService("Intranet", "Internal news and documents", BusinessCriticality.LOW);
        intranet.addDependency(storage);
        services.save(intranet);

        // Generic policies: the spec's example table.
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P3, Duration.ofHours(1), Duration.ofHours(8)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P4, Duration.ofHours(4), Duration.ofHours(24)));
        // A tighter, service-specific policy: payment outages cost money every minute.
        slaPolicies.save(new SlaPolicy(PriorityLevel.P1, payment, Duration.ofMinutes(3), Duration.ofMinutes(20),
                0.70));
    }

    private void seedPeople(Map<String, Category> category) {
        users.save(new Administrator("Meera Iyer", "admin@iitsm.example.com", DEMO_PASSWORD, hasher));

        Technician ravi = technician("Ravi Kumar", "ravi", SupportLevel.L1_TECHNICIAN, 3, 6);
        ravi.addOrUpdateSkill(category.get("Network"), 5);
        ravi.addOrUpdateSkill(category.get("Hardware"), 4);

        Technician priya = technician("Priya Sharma", "priya", SupportLevel.L1_TECHNICIAN, 4, 6);
        priya.addOrUpdateSkill(category.get("Application"), 5);
        priya.addOrUpdateSkill(category.get("Database"), 3);
        priya.addOrUpdateSkill(category.get("Software"), 4);

        Technician arjun = technician("Arjun Menon", "arjun", SupportLevel.L1_TECHNICIAN, 1, 4);
        arjun.addOrUpdateSkill(category.get("Software"), 3);
        arjun.addOrUpdateSkill(category.get("Hardware"), 3);
        arjun.addOrUpdateSkill(category.get("Authentication"), 2);

        Technician kavya = technician("Kavya Reddy", "kavya", SupportLevel.L2_TEAM_LEAD, 8, 5);
        kavya.addOrUpdateSkill(category.get("Database"), 5);
        kavya.addOrUpdateSkill(category.get("Cloud"), 4);
        kavya.addOrUpdateSkill(category.get("Application"), 4);
        kavya.addOrUpdateSkill(category.get("Storage"), 4);

        Technician farhan = technician("Farhan Ali", "farhan", SupportLevel.L3_SYSTEM_ADMIN, 12, 4);
        farhan.addOrUpdateSkill(category.get("Security"), 5);
        farhan.addOrUpdateSkill(category.get("Authentication"), 5);
        farhan.addOrUpdateSkill(category.get("Network"), 4);
        farhan.addOrUpdateSkill(category.get("Cloud"), 4);

        Technician sunita = technician("Sunita Rao", "sunita", SupportLevel.L4_IT_MANAGER, 18, 3);
        sunita.addOrUpdateSkill(category.get("Application"), 3);
        sunita.addOrUpdateSkill(category.get("Database"), 3);

        for (Technician technician : new Technician[]{ravi, priya, arjun, kavya, farhan, sunita}) {
            users.save(technician);
        }

        users.save(new Employee("Asha Patel", "asha@iitsm.example.com", DEMO_PASSWORD, hasher, "Finance", "Hyderabad"));
        users.save(new Employee("Vikram Singh", "vikram@iitsm.example.com", DEMO_PASSWORD, hasher, "Sales", "Mumbai"));
        users.save(new Employee("Neha Gupta", "neha@iitsm.example.com", DEMO_PASSWORD, hasher, "HR", "Pune"));
    }

    private Technician technician(String name, String login, SupportLevel level, int years, int capacity) {
        return new Technician(name, login + "@iitsm.example.com", DEMO_PASSWORD, hasher, level, years, capacity);
    }
}

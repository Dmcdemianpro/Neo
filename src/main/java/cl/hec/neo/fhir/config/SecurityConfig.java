package cl.hec.neo.fhir.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Usuario para el frontend
        UserDetails frontendUser = User.builder()
                .username("fhiruser")
                .password(passwordEncoder.encode("fhirpass123"))
                .roles("USER", "FHIR_CLIENT")
                .build();

        // Usuario administrador
        UserDetails adminUser = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("USER", "ADMIN", "FHIR_CLIENT")
                .build();

        return new InMemoryUserDetailsManager(frontendUser, adminUser);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Deshabilitar CSRF para API REST
            .authorizeHttpRequests(auth -> auth
                // Permitir acceso público a metadata (CapabilityStatement)
                .requestMatchers("/fhir/metadata").permitAll()
                // Permitir acceso a actuator para health checks
                .requestMatchers("/actuator/health").permitAll()
                // Requerir autenticación para todas las demás operaciones FHIR
                .requestMatchers("/fhir/**").authenticated()
                // Permitir todo lo demás (por si hay otros endpoints)
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {}); // Habilitar autenticación HTTP Basic

        return http.build();
    }
}

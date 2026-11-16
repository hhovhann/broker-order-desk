package dev.brokerdesk.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless API security: HTTP Basic keeps the sample self-contained; in production the same roles come from the bank's
 * OIDC provider (resource server, JWT). Method security enforces roles and account ownership on each endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())   // stateless, no session cookie to forge
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService users(PasswordEncoder enc, BrokerProperties props) {
        String pw = enc.encode(props.demoPassword());
        return new InMemoryUserDetailsManager(
                User.withUsername("trader1").password(pw).roles("TRADER").build(),
                User.withUsername("trader2").password(pw).roles("TRADER").build(),
                User.withUsername("risk1").password(pw).roles("RISK").build(),
                User.withUsername("lp1").password(pw).roles("LIQUIDITY_PROVIDER").build(),
                User.withUsername("lp2").password(pw).roles("LIQUIDITY_PROVIDER").build(),
                User.withUsername("admin").password(pw).roles("ADMIN").build());
    }
}

package com.eservice1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Endpoints that intentionally allow anonymous access. JwtFilter uses this
     * same list to avoid parsing bearer tokens for public requests.
     */
    public static final String[] PUBLIC_URL_PATTERNS = {
            "/auth/login",
            "/auth/owner",
            "/login",
            "/customer-login",
            "/services/**",
            "/service-categories/active",
            "/employee/forgot-password/send-otp",
            "/employee/forgot-password/verify-otp",
            "/employee/forgot-password/reset",
    };

    private final JwtFilter jwtFilter;

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(
            JwtFilter jwtFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint) {

        this.jwtFilter = jwtFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;

    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {
        //System.out.println("SECURITY CONFIG LOADED");
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()


                        .requestMatchers(PUBLIC_URL_PATTERNS).permitAll()

                        // SEC-CRIT-02: Public READ for form fields, OWNER only for mutating operations
                        .requestMatchers(HttpMethod.GET, "/customer-form-fields", "/customer-form-fields/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/service-form-fields", "/service-form-fields/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/customer-form-fields", "/customer-form-fields/**").hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/customer-form-fields", "/customer-form-fields/**").hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/customer-form-fields", "/customer-form-fields/**").hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.POST, "/service-form-fields", "/service-form-fields/**").hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/service-form-fields", "/service-form-fields/**").hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/service-form-fields", "/service-form-fields/**").hasAuthority("OWNER")

                        .requestMatchers("/auth/register")
                        .hasAuthority("OWNER")

                        .requestMatchers(
                                "/customer/send-otp",
                                "/customer/verify-otp"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/requests")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/requests/phone/**")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/requests/*/payment")
                        .hasAnyAuthority("OWNER", "EMPLOYEE")
                        .requestMatchers("/requests/**")
                        .hasAnyAuthority("CUSTOMER", "EMPLOYEE", "OWNER")

                        .requestMatchers("/documents/**")
                        .hasAnyAuthority("CUSTOMER", "EMPLOYEE", "OWNER")

                        .requestMatchers("/customer/profile/**")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers("/customer-form-responses/**")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers("/service-form-responses/**")
                        .hasAnyAuthority("CUSTOMER", "EMPLOYEE", "OWNER")

                        .requestMatchers("/receipts/**")
                        .hasAnyAuthority("EMPLOYEE", "OWNER")

                        .requestMatchers(HttpMethod.POST, "/feedback")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers("/feedback/**")
                        .hasAuthority("CUSTOMER")

                        .requestMatchers("/dashboard", "/dashboard/**")
                        .hasAuthority("OWNER")

                        .requestMatchers("/admin/**")
                        .hasAuthority("OWNER")
                        .requestMatchers("/users/**")
                        .hasAuthority("OWNER")

                        .requestMatchers("/employees/me/**")
                        .hasAnyAuthority("EMPLOYEE", "OWNER")

                        .requestMatchers("/employees/dashboard")
                        .hasAuthority("OWNER")

                        .requestMatchers(HttpMethod.POST, "/employees")
                        .hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.POST, "/employees/promote/**")
                        .hasAuthority("OWNER")

                        .requestMatchers("/employees/**")
                        .hasAnyAuthority(
                                "OWNER",
                                "EMPLOYEE"
                        )

                        .requestMatchers(HttpMethod.POST, "/employee/tasks/*/assign/*")
                        .hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.GET, "/employee/tasks")
                        .hasAuthority("OWNER")
                        .requestMatchers(HttpMethod.POST, "/employee/tasks")
                        .hasAuthority("OWNER")

                        .requestMatchers("/employee/**")
                        .hasAnyAuthority(
                                "OWNER",
                                "EMPLOYEE"
                        )
                        .requestMatchers(
                                "/employee/forgot-password/send-otp",
                                "/employee/forgot-password/verify-otp",
                                "/employee/forgot-password/reset"
                        ).permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/service-categories/**")
                        .hasAuthority("OWNER")
                        .anyRequest()
                        .authenticated()

                );
        http.addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://nellaieseva.com","https://www.nellaieseva.com"        ));

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}

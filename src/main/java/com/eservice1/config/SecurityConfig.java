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
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(
            JwtFilter jwtFilter) {

        this.jwtFilter = jwtFilter;

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

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()


                        .requestMatchers(
                                "/auth/login",
                                "/auth/owner"
                        ).permitAll()

                        .requestMatchers("/auth/register")
                        .hasAuthority("OWNER")

                        .requestMatchers(
                                "/admin/requests/test"
                        )
                        .permitAll()
                        .requestMatchers("/documents/download/**")
                        .permitAll()
                        .requestMatchers(
                                "/customer/**"
                        )
                        .permitAll()

                        //.requestMatchers(
                        //    HttpMethod.GET,
                        //    "/customer-form-fields/active"
                        //).hasAnyRole(
                        //    "CUSTOMER",
                        //    "EMPLOYEE",
                        //    "OWNER"
                        //).requestMatchers("/admin/**")
                        //.hasAuthority("OWNER")

                        //.requestMatchers("/employees/**")
                        //.hasAuthority("OWNER")

                        .requestMatchers("/admin/**")
                        .hasAnyAuthority(
                                "OWNER",
                                "EMPLOYEE"
                        )
                        .requestMatchers(
                                "/login",
                                "/customer-login",
                                "/customer/profile/**"
                        ).permitAll()
                        .requestMatchers("/users/**")
                        .hasAuthority("OWNER")

                        .requestMatchers("/employees/me/**")
                        .hasAnyAuthority("EMPLOYEE", "OWNER")

                        .requestMatchers("/employees/dashboard")
                        .permitAll()

                        .requestMatchers("/uploads/**")
                        .permitAll()

                        .requestMatchers("/employees/**")
                        .hasAnyAuthority(
                                "OWNER",
                                "EMPLOYEE"
                        )

                        .requestMatchers("/employee/**")
                        .hasAnyAuthority(
                                "OWNER",
                                "EMPLOYEE"
                        )
                        .requestMatchers("/requests/**")
                        .permitAll()
                        .requestMatchers(
                                "/services/**"
                        ).permitAll()
                        .requestMatchers("/service-categories/active")
                        .permitAll()

                        .requestMatchers("/service-categories/**")
                        .hasAuthority("OWNER")
                        .requestMatchers("/customer-form-fields/**")
                        .permitAll()

                        .requestMatchers(
                                "/customer-form-responses/**"
                        )
                        .permitAll()

                        .requestMatchers("/service-form-fields/**")
                        .permitAll()

                        .requestMatchers("/service-form-responses/**")
                        .permitAll()

                        .requestMatchers(
                                "/documents/request/**"
                        )
                        .permitAll()

                        .requestMatchers("/documents/upload")
                        .authenticated()


                        .requestMatchers("/feedback/**")
                        .permitAll()
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

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173",
                        "https://eservice1-frontend.onrender.com"
                )
        );

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
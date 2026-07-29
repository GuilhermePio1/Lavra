package dev.lavra.shared.security;

import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One security configuration for every environment. Only {@code issuer-uri}
 * differs between local development and Azure (ADR-0012) — there is no profile
 * that relaxes authentication, no mocked decoder and no {@code permitAll} on a
 * business route.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ApiErrorAuthenticationEntryPoint apiErrorHandler) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // The only exception allowed by spec 0004: health probes.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // The resource server installs its own entry point for
                        // bearer-token failures, and it wins over the one below.
                        // Without these two lines an expired or tampered token
                        // answers 401 with an empty body instead of ApiError —
                        // the contract's error shape would hold only for the
                        // no-token case.
                        .authenticationEntryPoint(apiErrorHandler)
                        .accessDeniedHandler(apiErrorHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiErrorHandler)
                        .accessDeniedHandler(apiErrorHandler))
                // Stateless bearer-token API: no session to fixate, no cookie to
                // forge, so CSRF protection has nothing to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Maps the Entra app-role claim onto Spring authorities, so {@code ADMIN}
     * from the token becomes {@code ROLE_ADMIN}. The OIDC emulator emits the
     * same {@code roles} claim, which is what keeps local and production
     * behaviour identical.
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = rolesConverter.convert(jwt);
            return authorities != null ? authorities : List.<GrantedAuthority>of();
        });
        return converter;
    }
}

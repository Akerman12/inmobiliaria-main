package com.example.inmobiliaria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain chatSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new AntPathRequestMatcher("/api/v2/chat**"))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // Login ADMIN (PÚBLICO)
                .requestMatchers(
                    "/admin/login.html",
                    "/css/**",
                    "/js/**",
                    "/img/**"
                    
                ).permitAll()

                // Páginas públicas
                .requestMatchers(
                     "/",
                    "/index.html",
                    "/nosotros.html",
                   "/anuncio.html",
                     "/anuncios.html",
                     "/blog.html",
                     "/contacto.html",
                     "/propiedad.html",
                     "/css/**",
                     "/js/**",
                     "/img/**",
                     "/admin/login",
                    "/admin/login.html",
                      "/api/prediccion/**"
                 ).permitAll()

                        .requestMatchers(new AntPathRequestMatcher("/api/v2/chat**", "POST")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/v2/chat/salud", "GET")).permitAll()

                    .requestMatchers(HttpMethod.GET, "/api/propiedades/**", "/api/imagenes/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/contacto").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/propiedades/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/propiedades/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/propiedades/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/propiedades/**").hasRole("ADMIN")
                     .requestMatchers(HttpMethod.POST, "/api/imagenes/subir").hasRole("ADMIN")

                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    .requestMatchers("/api/propietario/**").hasRole("PROPIETARIO")
                    .requestMatchers("/api/cliente/**").hasRole("CLIENTE")

                // Admin protegido
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/propietario/**").hasRole("PROPIETARIO")
                .requestMatchers("/cliente/**").hasRole("CLIENTE")

                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/admin/login.html")    
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    boolean isPropietario = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_PROPIETARIO"));

                    if (isAdmin) {
                        response.sendRedirect("/admin/index.html");
                    } else if (isPropietario) {
                        response.sendRedirect("/propietario/index.html");
                    } else {
                        response.sendRedirect("/cliente/index.html");
                    }
                })
                .failureUrl("/admin/login.html?error=true")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/admin/login.html?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
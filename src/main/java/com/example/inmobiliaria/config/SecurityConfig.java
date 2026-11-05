package com.example.inmobiliaria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final boolean secureCookies;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.userDetailsService = userDetailsService;
        this.secureCookies = secureCookies;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository(secureCookies))
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

            .authorizeHttpRequests(auth -> auth
                // Login ADMIN (PÚBLICO)
                .requestMatchers(
                    "/admin/login.html",
                    "/css/**",
                    "/js/**",
                    "/img/**"
                    
                ).permitAll()

                   .requestMatchers(HttpMethod.POST, "/api/cuenta/registro").permitAll()
                   .requestMatchers("/api/cuenta/**").authenticated()
                   .requestMatchers("/api/csrf").permitAll()

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
                     "/favoritos.html",
                     "/valoracion.html",
                     "/ingresar.html",
                     "/terminos.html",
                     "/privacidad.html",
                     "/css/**",
                     "/js/**",
                     "/img/**",
                     "/admin/login",
                    "/admin/login.html",
                      "/api/prediccion/**"
                 ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v2/chat", "/api/v2/chat/rapido").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/chat/salud").permitAll()

                    .requestMatchers(HttpMethod.GET, "/api/propiedades/visibles", "/api/propiedades/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/imagenes/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/propiedades/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/testimonios/visibles").permitAll()
                    .requestMatchers("/api/testimonios/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/contacto").permitAll()
                    .requestMatchers("/api/contacto/**").hasRole("ADMIN")
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

            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/api/cuenta/**")
            ))

            .formLogin(form -> form
                .loginPage("/admin/login.html")    
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler())
                .failureHandler((request, response, exception) -> {
                    String page = request.getParameter("publicLogin") != null
                            ? "/ingresar.html?error=true"
                            : "/admin/login.html?error=true";
                    response.sendRedirect(page);
                })
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    response.sendRedirect(isAdmin
                            ? "/admin/login.html?logout=true"
                            : "/ingresar.html?logout=true");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    CookieCsrfTokenRepository csrfTokenRepository(boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(secure)
                .sameSite("Lax"));
        return repository;
    }

    AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isPropietario = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PROPIETARIO"));
            boolean isCliente = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
            boolean publicLogin = request.getParameter("publicLogin") != null;
            boolean adminLogin = request.getParameter("adminLogin") != null;

            if (publicLogin && !adminLogin && (isPropietario || isCliente)) {
                response.sendRedirect(isPropietario
                        ? "/propietario/index.html"
                        : "/cliente/index.html");
            } else if (adminLogin && !publicLogin && isAdmin) {
                response.sendRedirect("/admin/index.html");
            } else {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                response.sendRedirect(publicLogin
                        ? "/ingresar.html?error=role"
                        : "/admin/login.html?error=role");
            }
        };
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
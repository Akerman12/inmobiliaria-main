package com.example.inmobiliaria.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityConfigTest {

    private final SecurityConfig securityConfig =
            new SecurityConfig(mock(CustomUserDetailsService.class));

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminPuedeEntrarAlPanelAdministrativo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("adminLogin", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.loginSuccessHandler().onAuthenticationSuccess(
                request, response, autenticacion("ADMIN"));

        assertEquals("/admin/index.html", response.getRedirectedUrl());
    }

    @Test
    void clienteNoPuedeUsarElAccesoAdministrativo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(autenticacion("CLIENTE"));

        securityConfig.loginSuccessHandler().onAuthenticationSuccess(
                request, response, autenticacion("CLIENTE"));

        assertEquals("/admin/login.html?error=role", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
    }

    @Test
    void propietarioNoPuedeUsarElAccesoAdministrativo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("adminLogin", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(autenticacion("PROPIETARIO"));

        securityConfig.loginSuccessHandler().onAuthenticationSuccess(
                request, response, autenticacion("PROPIETARIO"));

        assertEquals("/admin/login.html?error=role", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void propietarioEntraAlPortalDesdeElAccesoPublico() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("publicLogin", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.loginSuccessHandler().onAuthenticationSuccess(
                request, response, autenticacion("PROPIETARIO"));

        assertEquals("/propietario/index.html", response.getRedirectedUrl());
    }

    private UsernamePasswordAuthenticationToken autenticacion(String rol) {
        return new UsernamePasswordAuthenticationToken(
                "usuario",
                "credencial",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}

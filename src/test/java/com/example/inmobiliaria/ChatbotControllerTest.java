package com.example.inmobiliaria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.chat.ChatContextService;
import com.example.inmobiliaria.chat.ChatContextService.ChatContexto;
import com.example.inmobiliaria.chat.ChatContextService.IdentidadChat;
import com.example.inmobiliaria.chat.ChatRole;
import com.example.inmobiliaria.chat.ChatbotController;
import com.example.inmobiliaria.chat.ChatbotRateLimiter;
import com.example.inmobiliaria.chat.SpringAiChatService;
import com.example.inmobiliaria.config.CustomUserDetailsService;
import com.example.inmobiliaria.config.SecurityConfig;
import com.example.inmobiliaria.controller.CsrfController;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = {ChatbotController.class, CsrfController.class}, properties = "GROQ_API_KEY=test-key")
@Import(SecurityConfig.class)
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private ChatContextService chatContextService;

    @MockitoBean
    private ChatbotRateLimiter rateLimiter;

    @MockitoBean
    private SpringAiChatService springAiChatService;

    @Test
    void permiteVisitanteConCsrfYDevuelveRespuestaFiltrada() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.VISITANTE, null));
        when(rateLimiter.tryConsume(anyString(), eq(false))).thenReturn(true);
        when(chatContextService.construirContexto(any()))
                .thenReturn(new ChatContexto(ChatRole.VISITANTE, null, "Alcance visitante", "Casa Cartagena"));
        when(springAiChatService.responderChatbot(any(), anyList(), eq("¿Qué casas tienen?")))
                .thenReturn("Tenemos Casa Cartagena disponible.");

        CsrfDatos csrf = obtenerCsrf(null);

        mockMvc.perform(post("/api/chatbot")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"¿Qué casas tienen?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Tenemos Casa Cartagena disponible."))
                .andExpect(jsonPath("$.rol").value("VISITANTE"))
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void rechazaSinCsrfCon403() throws Exception {
        mockMvc.perform(post("/api/chatbot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaMensajeMayorA500Caracteres() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.VISITANTE, null));
        when(rateLimiter.tryConsume(anyString(), anyBoolean())).thenReturn(true);

        CsrfDatos csrf = obtenerCsrf(null);
        String largo = "a".repeat(501);

        mockMvc.perform(post("/api/chatbot")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"" + largo + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void devuelve429CuandoSuperaRateLimit() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.CLIENTE, "cli-1"));
        when(rateLimiter.tryConsume("USER:cli-1", true)).thenReturn(false);

        MockHttpSession session = sesionAutenticada("cliente@demo.com", "ROLE_CLIENTE");
        CsrfDatos csrf = obtenerCsrf(session);

        mockMvc.perform(post("/api/chatbot")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void devuelve401CuandoSesionEsInvalida() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida."));

        MockHttpSession session = sesionAutenticada("cliente@demo.com", "ROLE_CLIENTE");
        CsrfDatos csrf = obtenerCsrf(session);

        mockMvc.perform(post("/api/chatbot")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Mis visitas\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ignoraRolEnviadoEnBodyYUsaIdentidadDelSecurityContext() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.CLIENTE, "cli-1"));
        when(rateLimiter.tryConsume("USER:cli-1", true)).thenReturn(true);
        when(chatContextService.construirContexto(any()))
                .thenReturn(new ChatContexto(ChatRole.CLIENTE, "cli-1", "Alcance cliente", "Contexto cliente"));
        when(springAiChatService.responderChatbot(any(), anyList(), eq("Ver resumen")))
                .thenReturn("Resumen de cliente.");

        MockHttpSession session = sesionAutenticada("cliente@demo.com", "ROLE_CLIENTE");
        CsrfDatos csrf = obtenerCsrf(session);

        mockMvc.perform(post("/api/chatbot")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Ver resumen\",\"rol\":\"ADMIN\",\"userId\":\"otro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        verify(chatContextService).construirContexto(any());
    }

    @Test
    void estadoPublicoDevuelveDisponibilidadYRol() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.VISITANTE, null));

        mockMvc.perform(get("/api/chatbot/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.rol").value("VISITANTE"));
    }

    private MockHttpSession sesionAutenticada(String username, String authority) {
        MockHttpSession session = new MockHttpSession();
        var auth = new UsernamePasswordAuthenticationToken(
                username, "N/A", List.of(new SimpleGrantedAuthority(authority)));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        return session;
    }

    private CsrfDatos obtenerCsrf(MockHttpSession session) throws Exception {
        var req = get("/api/csrf");
        if (session != null) {
            req.session(session);
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            cookie = new Cookie("XSRF-TOKEN", token);
        }
        return new CsrfDatos(token, cookie);
    }

    private record CsrfDatos(String token, Cookie cookie) {
    }
}

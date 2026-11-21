package com.example.inmobiliaria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.inmobiliaria.chat.ChatContextService;
import com.example.inmobiliaria.chat.ChatContextService.IdentidadChat;
import com.example.inmobiliaria.chat.ChatRole;
import com.example.inmobiliaria.chat.ChatbotController;
import com.example.inmobiliaria.chat.ChatbotRateLimiter;
import com.example.inmobiliaria.config.CustomUserDetailsService;
import com.example.inmobiliaria.config.SecurityConfig;
import com.example.inmobiliaria.controller.CsrfController;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = {ChatbotController.class, CsrfController.class})
@Import(SecurityConfig.class)
class ChatbotDisabledControllerTest {

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

    @Test
    void desactivaConEleganciaCuandoGroqApiKeyNoEstaConfigurada() throws Exception {
        when(chatContextService.resolverIdentidad(any()))
                .thenReturn(new IdentidadChat(ChatRole.VISITANTE, null));
        when(rateLimiter.tryConsume(anyString(), eq(false))).thenReturn(true);

        mockMvc.perform(get("/api/chatbot/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.rol").value("VISITANTE"));

        MvcResult csrfResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            cookie = new Cookie("XSRF-TOKEN", token);
        }

        mockMvc.perform(post("/api/chatbot")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }
}

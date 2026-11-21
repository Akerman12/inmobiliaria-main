package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.example.inmobiliaria.chat.ChatContextService.ChatContexto;
import com.example.inmobiliaria.chat.ChatRole;
import com.example.inmobiliaria.chat.SpringAiChatService;

class SpringAiChatServiceTest {

    @Test
    void construyePromptSistemaConReglasRolAlcanceYContexto() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        SpringAiChatService service = new SpringAiChatService(builder);
        ChatContexto contexto = new ChatContexto(
                ChatRole.CLIENTE,
                "cli-1",
                "Solo propiedades visibles y tus propias visitas.",
                "- Casa Niza | Venta | $500.000.000 COP");

        String prompt = service.construirPromptSistema(contexto);

        assertTrue(prompt.contains("Belar Inmobiliaria (Colombia)"));
        assertTrue(prompt.contains("ROL DEL USUARIO: CLIENTE"));
        assertTrue(prompt.contains("ALCANCE PERMITIDO: Solo propiedades visibles y tus propias visitas."));
        assertTrue(prompt.contains("No tengo acceso a esa información con tu cuenta."));
        assertTrue(prompt.contains("Casa Niza"));
    }
}


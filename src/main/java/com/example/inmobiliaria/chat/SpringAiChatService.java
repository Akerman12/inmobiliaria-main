package com.example.inmobiliaria.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "groq", name = "api-key")
public class SpringAiChatService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelo;

    public SpringAiChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String responder(String pregunta, String dominio) {
        String promptSistema = (dominio != null && !dominio.isBlank())
                ? "Eres un asistente experto en " + dominio + ". Responde de forma clara, "
                  + "concisa y en español, salvo que se pida lo contrario."
                : "Eres un asistente útil. Responde de forma clara y concisa, en español.";

        return chatClient.prompt()
                .system(promptSistema)
                .user(pregunta)
                .call()
                .content();
    }

    public String getModelo() {
        return modelo;
    }
}
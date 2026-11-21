package com.example.inmobiliaria.chat;

public class ChatDatos {

    public record ChatRequest(String pregunta, String dominio) {
    }

    public record ChatResponse(String respuesta, String modelo, String dominio) {
    }

    public record ChatbotRequest(String mensaje) {
    }

    public record ChatbotResponse(String respuesta, String rol, boolean disponible) {
    }

    public record ChatbotEstadoResponse(boolean disponible, String rol) {
    }

    public record TurnoHistorial(String role, String content) {
    }
}

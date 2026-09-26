package com.example.inmobiliaria.chat;

public class ChatDatos {

    public record ChatRequest(String pregunta, String dominio) {
    }

    public record ChatResponse(String respuesta, String modelo, String dominio) {
    }
}
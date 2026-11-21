package com.example.inmobiliaria.chat;

public enum ChatRole {
    VISITANTE("VISITANTE"),
    CLIENTE("CLIENTE"),
    PROPIETARIO("PROPIETARIO"),
    ADMIN("ADMIN");

    private final String etiqueta;

    ChatRole(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}


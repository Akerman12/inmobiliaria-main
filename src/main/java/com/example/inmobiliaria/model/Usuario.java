package com.example.inmobiliaria.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;
    
    private String nombre;
    private String celular;

    @Indexed(name = "celular_normalizado_unique_idx", unique = true, sparse = true)
    private String celularNormalizado;
    
    @Indexed(unique = true)
    private String username; // Puede ser email o nombre de usuario
    
    @Indexed(unique = true)
    private String email;
    
    private String password; // Se almacena hasheada con BCrypt
    
    private String rol; // ADMIN, PROPIETARIO o USER
    
    private boolean enabled = true; // Para activar/desactivar usuarios
    private List<String> favoritos = new ArrayList<>();

    // Constructores
    public Usuario() {
    }

    public Usuario(String nombre, String username, String email, String password, String rol) {
        this.nombre = nombre;
        this.username = username;
        this.email = email;
        this.password = password;
        this.rol = rol;
        this.enabled = true;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getCelularNormalizado() {
        return celularNormalizado;
    }

    public void setCelularNormalizado(String celularNormalizado) {
        this.celularNormalizado = celularNormalizado;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getFavoritos() {
        return favoritos == null ? List.of() : favoritos;
    }

    public void setFavoritos(List<String> favoritos) {
        this.favoritos = favoritos == null ? new ArrayList<>() : new ArrayList<>(favoritos);
    }
}
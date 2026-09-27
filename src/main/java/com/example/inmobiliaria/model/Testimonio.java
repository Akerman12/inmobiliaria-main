package com.example.inmobiliaria.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "testimonios")
public class Testimonio {

    @Id
    private String id;

    private String nombre;
    private String rol;      // Ej: "Cliente satisfecho", "Compradora", "Vendedor"
    private String mensaje;
    private int estrellas = 5; // 1 a 5
    private boolean visible = true;

    @org.springframework.data.annotation.CreatedDate
    private java.time.LocalDateTime fechaCreacion;

    public Testimonio() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public int getEstrellas() { return estrellas; }
    public void setEstrellas(int estrellas) { this.estrellas = estrellas; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public java.time.LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(java.time.LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
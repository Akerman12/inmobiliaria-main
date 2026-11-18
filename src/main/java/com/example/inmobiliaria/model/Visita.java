package com.example.inmobiliaria.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "visitas")
public class Visita {

    @Id
    private String id;
    private String propiedadId;
    private String propiedadTitulo;
    private String propietarioId;
    private String clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private LocalDateTime fechaHora;
    private LocalDateTime fechaAsignada;
    private String disponibilidadId;
    private String mensaje;
    private String estado = "SOLICITADA";

    @CreatedDate
    private LocalDateTime fechaSolicitud;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPropiedadId() { return propiedadId; }
    public void setPropiedadId(String propiedadId) { this.propiedadId = propiedadId; }

    public String getPropiedadTitulo() { return propiedadTitulo; }
    public void setPropiedadTitulo(String propiedadTitulo) { this.propiedadTitulo = propiedadTitulo; }

    public String getPropietarioId() { return propietarioId; }
    public void setPropietarioId(String propietarioId) { this.propietarioId = propietarioId; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public LocalDateTime getFechaAsignada() { return fechaAsignada; }
    public void setFechaAsignada(LocalDateTime fechaAsignada) { this.fechaAsignada = fechaAsignada; }

    public String getDisponibilidadId() { return disponibilidadId; }
    public void setDisponibilidadId(String disponibilidadId) { this.disponibilidadId = disponibilidadId; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
}
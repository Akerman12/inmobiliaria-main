package com.example.inmobiliaria.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "disponibilidadesVisita")
public class DisponibilidadVisita {

    @Id
    private String id;
    private String propietarioId;
    private LocalDateTime fechaHora;
    private boolean reservada;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPropietarioId() { return propietarioId; }
    public void setPropietarioId(String propietarioId) { this.propietarioId = propietarioId; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public boolean isReservada() { return reservada; }
    public void setReservada(boolean reservada) { this.reservada = reservada; }
}

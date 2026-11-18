package com.example.inmobiliaria.model;

import java.time.LocalDateTime;
import java.util.List;

public record PlanVisitas(
        int solicitudesPendientes,
        int solicitudesAsignadas,
        int solicitudesSinHorario,
        long minutosEsperaTotal,
        List<Asignacion> asignaciones) {

    public record Asignacion(
            String visitaId,
            String propiedadTitulo,
            String clienteNombre,
            LocalDateTime fechaSolicitada,
            LocalDateTime fechaAsignada,
            String disponibilidadId,
            long minutosEspera) {
    }

    public record Confirmacion(List<AsignacionConfirmada> asignaciones) { }

    public record AsignacionConfirmada(String visitaId, String disponibilidadId) { }
}

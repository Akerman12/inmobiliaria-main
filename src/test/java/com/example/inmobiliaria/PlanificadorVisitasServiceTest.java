package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inmobiliaria.Service.PlanificadorVisitasService;
import com.example.inmobiliaria.model.DisponibilidadVisita;
import com.example.inmobiliaria.model.PlanVisitas;
import com.example.inmobiliaria.model.Visita;

class PlanificadorVisitasServiceTest {

    private final PlanificadorVisitasService service = new PlanificadorVisitasService();
    private final LocalDateTime solicitud = LocalDateTime.of(2026, 12, 1, 8, 0);

    @Test
    void asignaLaMayorCantidadRespetandoLaFechaSolicitadaYMinimizandoEspera() {
        Visita visitaTemprana = visita("visita-temprana", solicitud.plusHours(1), solicitud);
        Visita visitaTardia = visita("visita-tardia", solicitud.plusHours(2), solicitud);

        PlanVisitas resultado = service.planificar(
                List.of(visitaTemprana, visitaTardia),
                List.of(
                        horario("horario-9", solicitud.plusHours(1)),
                        horario("horario-10", solicitud.plusHours(2))));

        assertEquals(2, resultado.solicitudesAsignadas());
        assertEquals(0, resultado.solicitudesSinHorario());
        assertEquals(solicitud.plusHours(1), resultado.asignaciones().get(0).fechaAsignada());
        assertEquals("visita-temprana", resultado.asignaciones().get(0).visitaId());
        assertEquals(solicitud.plusHours(2), resultado.asignaciones().get(1).fechaAsignada());
        assertEquals("visita-tardia", resultado.asignaciones().get(1).visitaId());
        assertEquals(180, resultado.minutosEsperaTotal());
    }

    @Test
    void maximizaLaCantidadDeVisitasAntesDeMinimizarLaEspera() {
        LocalDateTime fechaLejana = solicitud.plusDays(90);
        Visita solicitudLejana = visita("visita-lejana", fechaLejana, solicitud);
        Visita solicitudCercana = visita("visita-cercana", solicitud.plusHours(1), solicitud);

        PlanVisitas resultado = service.planificar(
                List.of(solicitudLejana, solicitudCercana),
                List.of(
                        horario("horario-9", solicitud.plusHours(1)),
                        horario("horario-lejano", fechaLejana)));

        assertEquals(2, resultado.solicitudesAsignadas());
        assertEquals("visita-cercana", resultado.asignaciones().get(0).visitaId());
        assertEquals("visita-lejana", resultado.asignaciones().get(1).visitaId());
    }

    @Test
    void dejaSinAsignarSolicitudesCuandoNoHayHorarioCompatible() {
        PlanVisitas resultado = service.planificar(
                List.of(visita("visita", solicitud.plusHours(2), solicitud)),
                List.of(horario("horario", solicitud.plusHours(1))));

        assertEquals(1, resultado.solicitudesPendientes());
        assertEquals(0, resultado.solicitudesAsignadas());
        assertEquals(1, resultado.solicitudesSinHorario());
        assertEquals(List.of(), resultado.asignaciones());
    }

    private Visita visita(String id, LocalDateTime fechaPreferida, LocalDateTime fechaSolicitud) {
        Visita visita = new Visita();
        visita.setId(id);
        visita.setEstado("SOLICITADA");
        visita.setFechaHora(fechaPreferida);
        visita.setFechaSolicitud(fechaSolicitud);
        visita.setPropiedadTitulo("Apartamento");
        visita.setClienteNombre("Cliente");
        return visita;
    }

    private DisponibilidadVisita horario(String id, LocalDateTime fechaHora) {
        DisponibilidadVisita horario = new DisponibilidadVisita();
        horario.setId(id);
        horario.setFechaHora(fechaHora);
        return horario;
    }
}

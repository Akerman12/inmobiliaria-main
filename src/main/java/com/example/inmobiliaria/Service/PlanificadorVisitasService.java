package com.example.inmobiliaria.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.DisponibilidadVisita;
import com.example.inmobiliaria.model.PlanVisitas;
import com.example.inmobiliaria.model.Visita;

@Service
public class PlanificadorVisitasService {

    private static final int MAX_SOLICITUDES = 50;
    private static final int MAX_HORARIOS = 50;
    private static final double TOLERANCIA = 1e-7;

    public PlanVisitas planificar(
            List<Visita> solicitudes,
            List<DisponibilidadVisita> horariosDisponibles) {
        List<Visita> pendientes = solicitudes.stream()
                .filter(visita -> "SOLICITADA".equalsIgnoreCase(visita.getEstado()))
                .toList();
        List<DisponibilidadVisita> horarios = horariosDisponibles.stream()
                .filter(horario -> !horario.isReservada())
                .toList();

        if (pendientes.size() > MAX_SOLICITUDES || horarios.size() > MAX_HORARIOS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El planificador admite hasta 50 solicitudes y 50 horarios disponibles por cálculo.");
        }

        List<ParCompatible> pares = new ArrayList<>();
        long esperaMaxima = 0;
        for (int i = 0; i < pendientes.size(); i++) {
            Visita visita = pendientes.get(i);
            if (visita.getId() == null || visita.getFechaHora() == null) {
                throw solicitudInvalida("Cada solicitud debe tener identificador y fecha solicitada.");
            }
            LocalDateTime fechaSolicitud = visita.getFechaSolicitud() == null
                    ? visita.getFechaHora()
                    : visita.getFechaSolicitud();
            for (int j = 0; j < horarios.size(); j++) {
                DisponibilidadVisita horario = horarios.get(j);
                if (horario.getId() == null || horario.getFechaHora() == null) {
                    throw solicitudInvalida("Cada horario disponible debe tener identificador y fecha.");
                }
                if (!horario.getFechaHora().isBefore(visita.getFechaHora())
                        && !horario.getFechaHora().isBefore(fechaSolicitud)) {
                    long espera = Duration.between(fechaSolicitud, horario.getFechaHora()).toMinutes();
                    pares.add(new ParCompatible(i, j, espera));
                    esperaMaxima = Math.max(esperaMaxima, espera);
                }
            }
        }

        if (pares.isEmpty()) {
            return new PlanVisitas(pendientes.size(), 0, pendientes.size(), 0, List.of());
        }

        int cantidadVariables = pares.size();
        double prioridadAsignacion = esperaMaxima
                * Math.min(pendientes.size(), horarios.size()) + 1.0;
        double[] coeficientesObjetivo = new double[cantidadVariables];
        for (int k = 0; k < cantidadVariables; k++) {
            coeficientesObjetivo[k] = prioridadAsignacion - pares.get(k).minutosEspera();
        }

        List<LinearConstraint> restricciones = new ArrayList<>();
        for (int i = 0; i < pendientes.size(); i++) {
            double[] limiteSolicitud = new double[cantidadVariables];
            for (int k = 0; k < cantidadVariables; k++) {
                if (pares.get(k).indiceSolicitud() == i) {
                    limiteSolicitud[k] = 1;
                }
            }
            restricciones.add(new LinearConstraint(limiteSolicitud, Relationship.LEQ, 1));
        }
        for (int j = 0; j < horarios.size(); j++) {
            double[] limiteHorario = new double[cantidadVariables];
            for (int k = 0; k < cantidadVariables; k++) {
                if (pares.get(k).indiceHorario() == j) {
                    limiteHorario[k] = 1;
                }
            }
            restricciones.add(new LinearConstraint(limiteHorario, Relationship.LEQ, 1));
        }

        PointValuePair solucion = new SimplexSolver().optimize(
                new MaxIter(10000),
                new LinearObjectiveFunction(coeficientesObjetivo, 0),
                new LinearConstraintSet(restricciones),
                GoalType.MAXIMIZE,
                new NonNegativeConstraint(true));

        double[] decisiones = solucion.getPoint();
        List<PlanVisitas.Asignacion> asignaciones = new ArrayList<>();
        long minutosEsperaTotal = 0;
        for (int k = 0; k < decisiones.length; k++) {
            if (decisiones[k] < 1 - TOLERANCIA) {
                continue;
            }
            ParCompatible par = pares.get(k);
            Visita visita = pendientes.get(par.indiceSolicitud());
            DisponibilidadVisita horario = horarios.get(par.indiceHorario());
            minutosEsperaTotal += par.minutosEspera();
            asignaciones.add(new PlanVisitas.Asignacion(
                    visita.getId(),
                    visita.getPropiedadTitulo(),
                    visita.getClienteNombre(),
                    visita.getFechaHora(),
                    horario.getFechaHora(),
                    horario.getId(),
                    par.minutosEspera()));
        }

        asignaciones.sort((primera, segunda) -> primera.fechaAsignada().compareTo(segunda.fechaAsignada()));
        int sinHorario = pendientes.size() - asignaciones.size();
        return new PlanVisitas(
                pendientes.size(),
                asignaciones.size(),
                sinHorario,
                minutosEsperaTotal,
                List.copyOf(asignaciones));
    }

    private ResponseStatusException solicitudInvalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private record ParCompatible(int indiceSolicitud, int indiceHorario, long minutosEspera) { }
}

package com.example.inmobiliaria.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.ModeloPromocion;

@Service
public class OptimizacionPromocionService {

    private static final double TOLERANCIA = 1e-9;

    public ModeloPromocion.Resultado optimizar(ModeloPromocion solicitud) {
        if (solicitud == null) {
            throw solicitudInvalida("Se requieren los datos del modelo.");
        }

        validarNoNegativo(solicitud.presupuestoPublicidadDisponibleMillones(),
                "El presupuesto disponible debe ser un número mayor o igual a cero.");
        validarNoNegativo(solicitud.horasAsesorDisponibles(),
                "Las horas disponibles deben ser un número mayor o igual a cero.");

        List<ModeloPromocion.Candidato> candidatos = solicitud.candidatos();
        if (candidatos == null || candidatos.isEmpty()) {
            throw solicitudInvalida("Agrega al menos una propiedad para optimizar.");
        }

        Set<String> ids = new HashSet<>();
        for (ModeloPromocion.Candidato candidato : candidatos) {
            validarCandidato(candidato, ids);
        }

        int cantidad = candidatos.size();
        double[] comisiones = new double[cantidad];
        List<LinearConstraint> restricciones = new ArrayList<>();
        double[] coeficientesPublicidad = new double[cantidad];
        double[] coeficientesHoras = new double[cantidad];

        for (int i = 0; i < cantidad; i++) {
            ModeloPromocion.Candidato candidato = candidatos.get(i);
            comisiones[i] = candidato.comisionEsperadaMillones();
            coeficientesPublicidad[i] = candidato.presupuestoPublicidadMillones();
            coeficientesHoras[i] = candidato.horasAsesor();

            double[] limiteIntensidad = new double[cantidad];
            limiteIntensidad[i] = 1;
            restricciones.add(new LinearConstraint(limiteIntensidad, Relationship.LEQ, 1));
        }

        restricciones.add(new LinearConstraint(
                coeficientesPublicidad,
                Relationship.LEQ,
                solicitud.presupuestoPublicidadDisponibleMillones()));
        restricciones.add(new LinearConstraint(
                coeficientesHoras,
                Relationship.LEQ,
                solicitud.horasAsesorDisponibles()));

        PointValuePair solucion = new SimplexSolver().optimize(
                new MaxIter(10000),
                new LinearObjectiveFunction(comisiones, 0),
                new LinearConstraintSet(restricciones),
                GoalType.MAXIMIZE,
                new NonNegativeConstraint(true));

        double[] intensidades = solucion.getPoint();
        List<ModeloPromocion.Decision> decisiones = new ArrayList<>(cantidad);
        double comisionTotal = 0;
        double publicidadUsada = 0;
        double horasUsadas = 0;

        for (int i = 0; i < cantidad; i++) {
            ModeloPromocion.Candidato candidato = candidatos.get(i);
            double intensidad = Math.max(0, Math.min(1, intensidades[i]));
            if (intensidad < TOLERANCIA) {
                intensidad = 0;
            }

            double comisionAportada = candidato.comisionEsperadaMillones() * intensidad;
            double publicidadAsignada = candidato.presupuestoPublicidadMillones() * intensidad;
            double horasAsignadas = candidato.horasAsesor() * intensidad;
            comisionTotal += comisionAportada;
            publicidadUsada += publicidadAsignada;
            horasUsadas += horasAsignadas;

            decisiones.add(new ModeloPromocion.Decision(
                    candidato.propiedadId(),
                    candidato.titulo(),
                    intensidad,
                    comisionAportada,
                    publicidadAsignada,
                    horasAsignadas));
        }

        return new ModeloPromocion.Resultado(
                comisionTotal,
                publicidadUsada,
                horasUsadas,
                solicitud.presupuestoPublicidadDisponibleMillones(),
                solicitud.horasAsesorDisponibles(),
                decisiones);
    }

    private void validarCandidato(ModeloPromocion.Candidato candidato, Set<String> ids) {
        if (candidato == null
                || candidato.propiedadId() == null
                || candidato.propiedadId().isBlank()
                || candidato.titulo() == null
                || candidato.titulo().isBlank()) {
            throw solicitudInvalida("Cada candidato debe tener un inmueble válido.");
        }
        if (!ids.add(candidato.propiedadId())) {
            throw solicitudInvalida("No se puede incluir dos veces la misma propiedad.");
        }
        validarNoNegativo(candidato.comisionEsperadaMillones(),
                "La comisión esperada debe ser un número mayor o igual a cero.");
        validarNoNegativo(candidato.presupuestoPublicidadMillones(),
                "El presupuesto de publicidad debe ser un número mayor o igual a cero.");
        validarNoNegativo(candidato.horasAsesor(),
                "Las horas de asesor deben ser un número mayor o igual a cero.");
    }

    private void validarNoNegativo(Double valor, String mensaje) {
        if (valor == null || !Double.isFinite(valor) || valor < 0) {
            throw solicitudInvalida(mensaje);
        }
    }

    private ResponseStatusException solicitudInvalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}

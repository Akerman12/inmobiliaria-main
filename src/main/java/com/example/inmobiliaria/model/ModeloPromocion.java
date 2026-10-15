package com.example.inmobiliaria.model;

import java.util.List;

public record ModeloPromocion(
        Double presupuestoPublicidadDisponibleMillones,
        Double horasAsesorDisponibles,
        List<Candidato> candidatos) {

    public record Candidato(
            String propiedadId,
            String titulo,
            Double comisionEsperadaMillones,
            Double presupuestoPublicidadMillones,
            Double horasAsesor) {
    }

    public record Resultado(
            double comisionTotalEsperadaMillones,
            double presupuestoPublicidadUsadoMillones,
            double horasAsesorUsadas,
            double presupuestoPublicidadDisponibleMillones,
            double horasAsesorDisponibles,
            List<Decision> decisiones) {
    }

    public record Decision(
            String propiedadId,
            String titulo,
            double intensidadPromocion,
            double comisionAportadaMillones,
            double presupuestoPublicidadAsignadoMillones,
            double horasAsesorAsignadas) {
    }
}

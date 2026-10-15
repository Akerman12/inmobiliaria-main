package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.Service.OptimizacionPromocionService;
import com.example.inmobiliaria.model.ModeloPromocion;

class OptimizacionPromocionServiceTest {

    private final OptimizacionPromocionService service = new OptimizacionPromocionService();

    @Test
    void maximizaComisionSinExcederPresupuestoNiHoras() {
        ModeloPromocion solicitud = new ModeloPromocion(
                6.0,
                3.0,
                List.of(
                        new ModeloPromocion.Candidato("propiedad-a", "Propiedad A", 12.0, 6.0, 3.0),
                        new ModeloPromocion.Candidato("propiedad-b", "Propiedad B", 10.0, 4.0, 2.0)));

        ModeloPromocion.Resultado resultado = service.optimizar(solicitud);

        assertEquals(14.0, resultado.comisionTotalEsperadaMillones(), 1e-7);
        assertEquals(6.0, resultado.presupuestoPublicidadUsadoMillones(), 1e-7);
        assertEquals(3.0, resultado.horasAsesorUsadas(), 1e-7);
        assertEquals(1.0, resultado.decisiones().get(1).intensidadPromocion(), 1e-7);
        assertEquals(1.0 / 3.0, resultado.decisiones().get(0).intensidadPromocion(), 1e-7);
    }

    @Test
    void rechazaPresupuestoNegativo() {
        ModeloPromocion solicitud = new ModeloPromocion(
                -1.0,
                3.0,
                List.of(new ModeloPromocion.Candidato("propiedad-a", "Propiedad A", 12.0, 6.0, 3.0)));

        assertThrows(ResponseStatusException.class, () -> service.optimizar(solicitud));
    }

    @Test
    void rechazaPropiedadesDuplicadas() {
        ModeloPromocion solicitud = new ModeloPromocion(
                6.0,
                3.0,
                List.of(
                        new ModeloPromocion.Candidato("propiedad-a", "Propiedad A", 12.0, 6.0, 3.0),
                        new ModeloPromocion.Candidato("propiedad-a", "Propiedad A", 10.0, 4.0, 2.0)));

        assertThrows(ResponseStatusException.class, () -> service.optimizar(solicitud));
    }
}

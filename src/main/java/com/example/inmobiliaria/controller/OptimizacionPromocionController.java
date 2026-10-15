package com.example.inmobiliaria.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inmobiliaria.Service.OptimizacionPromocionService;
import com.example.inmobiliaria.model.ModeloPromocion;

@RestController
@RequestMapping("/api/admin/optimizacion-promocion")
public class OptimizacionPromocionController {

    private final OptimizacionPromocionService optimizacionService;

    public OptimizacionPromocionController(OptimizacionPromocionService optimizacionService) {
        this.optimizacionService = optimizacionService;
    }

    @PostMapping("/simplex")
    public ModeloPromocion.Resultado optimizar(@RequestBody ModeloPromocion solicitud) {
        return optimizacionService.optimizar(solicitud);
    }
}

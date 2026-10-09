package com.example.inmobiliaria.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.model.Visita;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;
import com.example.inmobiliaria.repository.VisitaRepository;

@RestController
@RequestMapping("/api/propietario")
public class PropietarioController {

    private final PropiedadRepository propiedadRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContactoRepository contactoRepository;
    private final VisitaRepository visitaRepository;

    public PropietarioController(PropiedadRepository propiedadRepository,
                                 UsuarioRepository usuarioRepository,
                                 ContactoRepository contactoRepository,
                                 VisitaRepository visitaRepository) {
        this.propiedadRepository = propiedadRepository;
        this.usuarioRepository = usuarioRepository;
        this.contactoRepository = contactoRepository;
        this.visitaRepository = visitaRepository;
    }

    @GetMapping("/propiedades")
    public List<Propiedad> misPropiedades() {
        Usuario propietario = usuarioActual();
        List<Propiedad> propiedades = propiedadRepository.findByPropietarioId(propietario.getId());

        if (propiedades.isEmpty() && "propietario".equalsIgnoreCase(propietario.getUsername())) {
            return propiedadRepository.findAll();
        }

        return propiedades;
    }

    @GetMapping("/consultas")
    public List<com.example.inmobiliaria.model.Contacto> misConsultas() {
        Usuario propietario = usuarioActual();
        List<String> propiedadIds = propiedadRepository.findByPropietarioId(propietario.getId()).stream()
                .map(Propiedad::getId)
                .toList();

        if (propiedadIds.isEmpty()) {
            return List.of();
        }

        return contactoRepository.findByCasaInteresInOrderByFechaCreacionDesc(propiedadIds);
    }

    @GetMapping("/visitas")
    public List<Visita> misVisitas() {
        Usuario propietario = usuarioActual();
        return visitaRepository.findByPropietarioIdOrderByFechaHoraAsc(propietario.getId());
    }

    @PatchMapping("/visitas/{id}/estado")
    public ResponseEntity<Visita> actualizarEstadoVisita(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Usuario propietario = usuarioActual();
        Visita visita = visitaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!propietario.getId().equals(visita.getPropietarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar esta visita");
        }

        String nuevoEstado = body.get("estado");
        boolean transicionPermitida = "SOLICITADA".equals(visita.getEstado())
                && ("CONFIRMADA".equals(nuevoEstado) || "RECHAZADA".equals(nuevoEstado))
                || "CONFIRMADA".equals(visita.getEstado()) && "COMPLETADA".equals(nuevoEstado);
        if (!transicionPermitida) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transición de estado no permitida");
        }

        visita.setEstado(nuevoEstado);
        return ResponseEntity.ok(visitaRepository.save(visita));
    }

    @PatchMapping("/propiedades/{id}/estado")
    public ResponseEntity<Propiedad> actualizarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        Usuario propietario = usuarioActual();
        Propiedad propiedad = propiedadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada"));

        if (!propietario.getId().equals(propiedad.getPropietarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar una propiedad que no te pertenece");
        }

        String nuevoEstado = body.getOrDefault("estadoComercial", "DISPONIBLE");
        propiedad.setEstadoComercial(nuevoEstado);
        return ResponseEntity.ok(propiedadRepository.save(propiedad));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
}

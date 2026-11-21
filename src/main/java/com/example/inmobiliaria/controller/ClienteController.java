package com.example.inmobiliaria.controller;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.model.Visita;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;
import com.example.inmobiliaria.repository.VisitaRepository;

@RestController
@RequestMapping("/api/cliente")
public class ClienteController {

    private final PropiedadRepository propiedadRepository;
    private final UsuarioRepository usuarioRepository;
    private final VisitaRepository visitaRepository;

    public ClienteController(PropiedadRepository propiedadRepository,
                             UsuarioRepository usuarioRepository,
                             VisitaRepository visitaRepository) {
        this.propiedadRepository = propiedadRepository;
        this.usuarioRepository = usuarioRepository;
        this.visitaRepository = visitaRepository;
    }

    @GetMapping("/propiedades")
    public List<Propiedad> listarDisponibles() {
        return propiedadRepository.findAll()
                .stream()
                .filter(Propiedad::isVisible)
            .filter(propiedad -> !"EN_PROCESO".equalsIgnoreCase(propiedad.getEstadoComercial()))
                .toList();
    }

    @GetMapping("/visitas")
    public List<Visita> misVisitas(Authentication authentication) {
        Usuario cliente = usuarioActual(authentication);
        return visitaRepository.findByClienteIdOrderByFechaHoraAsc(cliente.getId());
    }

    @PostMapping("/visitas")
    public ResponseEntity<Visita> solicitarVisita(
            @RequestBody SolicitudVisita solicitud,
            Authentication authentication) {
        if (solicitud == null || solicitud.propiedadId() == null || solicitud.propiedadId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una propiedad");
        }
        if (solicitud.fechaHora() == null || !solicitud.fechaHora().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de visita debe ser futura");
        }
        if (solicitud.mensaje() != null && solicitud.mensaje().trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje no puede superar 1000 caracteres.");
        }

        Propiedad propiedad = propiedadRepository.findById(solicitud.propiedadId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada"));
        if (!propiedad.isVisible() || "EN_PROCESO".equalsIgnoreCase(propiedad.getEstadoComercial())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La propiedad no está disponible para visitas");
        }
        if (propiedad.getPropietarioId() == null || propiedad.getPropietarioId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La propiedad todavía no tiene un propietario asignado");
        }

        Usuario cliente = usuarioActual(authentication);
        Visita visita = new Visita();
        visita.setPropiedadId(propiedad.getId());
        visita.setPropiedadTitulo(propiedad.getTitulo());
        visita.setPropietarioId(propiedad.getPropietarioId());
        visita.setClienteId(cliente.getId());
        visita.setClienteNombre(cliente.getNombre());
        visita.setClienteEmail(cliente.getEmail());
        visita.setFechaHora(solicitud.fechaHora());
        visita.setMensaje(solicitud.mensaje() == null ? "" : solicitud.mensaje().trim());
        visita.setEstado("SOLICITADA");

        propiedad.setVisitas(propiedad.getVisitas() + 1);
        propiedadRepository.save(propiedad);
        return ResponseEntity.status(HttpStatus.CREATED).body(visitaRepository.save(visita));
    }

    private Usuario usuarioActual(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente no autenticado");
        }
        return usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente no encontrado"));
    }

    public record SolicitudVisita(String propiedadId, LocalDateTime fechaHora, String mensaje) { }
}

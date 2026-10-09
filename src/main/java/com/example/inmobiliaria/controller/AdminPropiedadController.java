package com.example.inmobiliaria.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.model.Usuario;
import com.example.inmobiliaria.repository.PropiedadRepository;
import com.example.inmobiliaria.repository.UsuarioRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminPropiedadController {

    private final PropiedadRepository propiedadRepository;
    private final UsuarioRepository usuarioRepository;

    public AdminPropiedadController(PropiedadRepository propiedadRepository,
                                    UsuarioRepository usuarioRepository) {
        this.propiedadRepository = propiedadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/propietarios")
    public List<PropietarioResumen> listarPropietarios() {
        return usuarioRepository.findByRolAndEnabledTrue("PROPIETARIO").stream()
                .sorted(Comparator.comparing(Usuario::getNombre,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(usuario -> new PropietarioResumen(
                        usuario.getId(), usuario.getNombre(), usuario.getUsername()))
                .toList();
    }

    @PatchMapping("/propiedades/{id}/propietario")
    public ResponseEntity<Propiedad> asignarPropietario(
            @PathVariable String id,
            @RequestBody AsignacionPropietario asignacion) {
        Propiedad propiedad = propiedadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Propiedad no encontrada"));

        String propietarioId = asignacion == null ? null : asignacion.propietarioId();
        if (propietarioId == null || propietarioId.isBlank()) {
            propiedad.setPropietarioId(null);
            return ResponseEntity.ok(propiedadRepository.save(propiedad));
        }

        Usuario propietario = usuarioRepository.findById(propietarioId)
                .filter(usuario -> "PROPIETARIO".equals(usuario.getRol()) && usuario.isEnabled())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "La cuenta seleccionada no es un propietario habilitado"));

        propiedad.setPropietarioId(propietario.getId());
        return ResponseEntity.ok(propiedadRepository.save(propiedad));
    }

    public record PropietarioResumen(String id, String nombre, String username) {
    }

    public record AsignacionPropietario(String propietarioId) {
    }
}
package com.example.inmobiliaria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.inmobiliaria.Service.CuentaService;
import com.example.inmobiliaria.model.Usuario;

@RestController
@RequestMapping("/api/cuenta")
public class CuentaRestController {

    private final CuentaService cuentaService;

    public CuentaRestController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping("/registro")
    public ResponseEntity<PerfilCuenta> registrar(@RequestBody RegistroCuenta solicitud) {
        if (solicitud == null) {
            return ResponseEntity.badRequest().build();
        }
        Usuario usuario = cuentaService.crearCuenta(
                solicitud.nombre(), solicitud.email(), solicitud.celular(),
                solicitud.password(), solicitud.tipoCuenta());
        return ResponseEntity.status(HttpStatus.CREATED).body(PerfilCuenta.de(usuario));
    }

    @GetMapping("/perfil")
    public PerfilCuenta perfil(Authentication authentication) {
        return PerfilCuenta.de(cuentaService.buscarPorUsername(authentication.getName()));
    }

    @PutMapping("/perfil")
    public PerfilCuenta actualizarPerfil(
            @RequestBody ActualizarPerfil solicitud,
            Authentication authentication) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos del perfil no son válidos.");
        }
        return PerfilCuenta.de(cuentaService.actualizarPerfil(
                authentication.getName(), solicitud.nombre(), solicitud.celular()));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> cambiarPassword(
            @RequestBody CambiarPassword solicitud,
            Authentication authentication) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de contraseña no son válidos.");
        }
        cuentaService.cambiarPassword(
                authentication.getName(), solicitud.passwordActual(), solicitud.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favoritos")
    public List<String> favoritos(Authentication authentication) {
        return cuentaService.obtenerFavoritos(authentication.getName());
    }

    @PutMapping("/favoritos")
    public List<String> actualizarFavoritos(
            @RequestBody ActualizarFavoritos solicitud,
            Authentication authentication) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista de favoritos no es válida.");
        }
        return cuentaService.actualizarFavoritos(authentication.getName(), solicitud.ids());
    }

    public record RegistroCuenta(String nombre, String email, String celular, String password, String tipoCuenta) { }

    public record ActualizarPerfil(String nombre, String celular) { }

    public record CambiarPassword(String passwordActual, String passwordNueva) { }

    public record ActualizarFavoritos(List<String> ids) { }

    public record PerfilCuenta(String nombre, String email, String celular, String rol) {
        private static PerfilCuenta de(Usuario usuario) {
            return new PerfilCuenta(usuario.getNombre(), usuario.getEmail(), usuario.getCelular(), usuario.getRol());
        }
    }
}

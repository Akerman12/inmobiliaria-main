package com.example.inmobiliaria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Contacto;
import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.repository.ContactoRepository;
import com.example.inmobiliaria.repository.PropiedadRepository;

@RestController
@RequestMapping("/api/contacto")
public class ContactoController {

    private final ContactoRepository contactoRepository;
    private final PropiedadRepository propiedadRepository;

    public ContactoController(ContactoRepository contactoRepository, PropiedadRepository propiedadRepository) {
        this.contactoRepository = contactoRepository;
        this.propiedadRepository = propiedadRepository;
    }

    // Guardar contacto (público)
    @PostMapping
    public Contacto guardarContacto(@RequestBody Contacto contacto) {
        if (contacto == null
                || !textoValido(contacto.getNombre(), 2, 100)
                || !correoValido(contacto.getCorreo())
                || !textoValido(contacto.getMensaje(), 1, 2000)
                || !textoOpcionalValido(contacto.getCelular(), 30)
                || !telefonoValido(contacto.getCelular())
                || !textoOpcionalValido(contacto.getCasaInteres(), 100)
                || contacto.getMedioContacto() == null
                || !List.of("correo", "telefono", "whatsapp").contains(contacto.getMedioContacto())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los datos del formulario de contacto.");
        }

        contacto.setNombre(contacto.getNombre().trim());
        contacto.setCorreo(contacto.getCorreo().trim());
        contacto.setMensaje(contacto.getMensaje().trim());
        contacto.setCelular(contacto.getCelular() == null ? "" : contacto.getCelular().trim());
        contacto.setCasaInteres(contacto.getCasaInteres() == null ? "" : contacto.getCasaInteres().trim());
        contacto.setId(null);
        contacto.setFechaCreacion(null);

        if (contacto.getCasaInteres() != null && !contacto.getCasaInteres().isBlank()) {
            Propiedad propiedad = propiedadRepository.findById(contacto.getCasaInteres())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "La propiedad seleccionada no existe"));
            if ("EN_PROCESO".equalsIgnoreCase(propiedad.getEstadoComercial())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "La propiedad está en proceso y no recibe consultas");
            }
        }

        contacto.setAtendido(false); // Siempre inicia como pendiente
        return contactoRepository.save(contacto);
    }

    private boolean textoValido(String valor, int minimo, int maximo) {
        return valor != null && valor.trim().length() >= minimo && valor.trim().length() <= maximo;
    }

    private boolean textoOpcionalValido(String valor, int maximo) {
        return valor == null || valor.trim().length() <= maximo;
    }

    private boolean correoValido(String correo) {
        return textoValido(correo, 3, 254)
                && correo.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private boolean telefonoValido(String telefono) {
        if (telefono == null || telefono.isBlank()) return true;
        long digitos = telefono.chars().filter(Character::isDigit).count();
        return telefono.length() <= 30 && telefono.matches("[0-9+() .-]+")
                && digitos >= 7 && digitos <= 15;
    }

    // Listar todos (admin)
    @GetMapping
    public List<Contacto> listarContactos() {
        return contactoRepository.findAll();
    }

    // Toggle atendido/pendiente (admin)
    @PatchMapping("/{id}/atendido")
    public Contacto toggleAtendido(@PathVariable String id) {
        Contacto c = contactoRepository.findById(id).orElse(null);
        if (c != null) {
            c.setAtendido(!c.isAtendido());
            contactoRepository.save(c);
        }
        return c;
    }

    // Eliminar contacto (admin)
    @DeleteMapping("/{id}")
    public void eliminarContacto(@PathVariable String id) {
        contactoRepository.deleteById(id);
    }
}

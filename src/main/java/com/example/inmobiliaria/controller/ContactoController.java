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
@CrossOrigin(origins = "*")
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

package com.example.inmobiliaria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.inmobiliaria.model.Testimonio;
import com.example.inmobiliaria.repository.TestimonioRepository;

@RestController
@RequestMapping("/api/testimonios")
public class TestimonioRestController {

    private final TestimonioRepository repo;

    public TestimonioRestController(TestimonioRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Testimonio> listar() {
        return repo.findAll();
    }

    @GetMapping("/visibles")
    public List<Testimonio> listarVisibles() {
        return repo.findAll()
                .stream()
                .filter(Testimonio::isVisible)
                .toList();
    }

    @GetMapping("/{id}")
    public Testimonio obtenerPorId(@PathVariable String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Testimonio no encontrado."));
    }

    @PostMapping
    public Testimonio crear(@RequestBody Testimonio testimonio) {
        validar(testimonio);
        testimonio.setId(null);
        limpiar(testimonio);
        return repo.save(testimonio);
    }

    @PutMapping("/{id}")
    public Testimonio actualizar(@PathVariable String id, @RequestBody Testimonio nuevo) {
        validar(nuevo);
        Testimonio existente = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Testimonio no encontrado."));

        existente.setNombre(nuevo.getNombre());
        existente.setRol(nuevo.getRol());
        existente.setMensaje(nuevo.getMensaje());
        existente.setEstrellas(nuevo.getEstrellas());
        limpiar(existente);

        return repo.save(existente);
    }

    @PatchMapping("/{id}/visibilidad")
    public Testimonio cambiarVisibilidad(@PathVariable String id) {
        Testimonio t = repo.findById(id).orElse(null);
        if (t != null) {
            t.setVisible(!t.isVisible());
            repo.save(t);
        }
        return t;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable String id) {
        repo.deleteById(id);
    }

    private void validar(Testimonio testimonio) {
        if (testimonio == null
                || !textoValido(testimonio.getNombre(), 2, 100)
                || !textoOpcionalValido(testimonio.getRol(), 100)
                || !textoValido(testimonio.getMensaje(), 1, 2000)
                || testimonio.getEstrellas() < 1
                || testimonio.getEstrellas() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los datos del testimonio.");
        }
    }

    private boolean textoValido(String valor, int minimo, int maximo) {
        return valor != null && valor.trim().length() >= minimo && valor.trim().length() <= maximo;
    }

    private boolean textoOpcionalValido(String valor, int maximo) {
        return valor == null || valor.trim().length() <= maximo;
    }

    private void limpiar(Testimonio testimonio) {
        testimonio.setNombre(testimonio.getNombre().trim());
        testimonio.setRol(testimonio.getRol() == null ? "" : testimonio.getRol().trim());
        testimonio.setMensaje(testimonio.getMensaje().trim());
    }
}
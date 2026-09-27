package com.example.inmobiliaria.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.inmobiliaria.model.Testimonio;
import com.example.inmobiliaria.repository.TestimonioRepository;

@RestController
@RequestMapping("/api/testimonios")
@CrossOrigin(origins = "*")
public class TestimonioRestController {

    @Autowired
    private TestimonioRepository repo;

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
        return repo.findById(id).orElse(null);
    }

    @PostMapping
    public Testimonio crear(@RequestBody Testimonio testimonio) {
        testimonio.setId(null);
        return repo.save(testimonio);
    }

    @PutMapping("/{id}")
    public Testimonio actualizar(@PathVariable String id, @RequestBody Testimonio nuevo) {
        Testimonio existente = repo.findById(id).orElse(null);
        if (existente == null) return null;

        existente.setNombre(nuevo.getNombre());
        existente.setRol(nuevo.getRol());
        existente.setMensaje(nuevo.getMensaje());
        existente.setEstrellas(nuevo.getEstrellas());

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
}
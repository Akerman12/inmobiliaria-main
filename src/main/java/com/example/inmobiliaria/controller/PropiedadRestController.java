package com.example.inmobiliaria.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.bson.types.ObjectId;

import com.example.inmobiliaria.Service.ImageStorageService;
import com.example.inmobiliaria.model.Propiedad;
import com.example.inmobiliaria.repository.PropiedadRepository;

@RestController
@RequestMapping("/api/propiedades")
@CrossOrigin(origins = "*")
public class PropiedadRestController {

    @Autowired
    private PropiedadRepository repo;

    @Autowired
    private ImageStorageService imageStorageService;

    @GetMapping
    public List<Propiedad> listar() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Propiedad obtenerPorId(@PathVariable String id) {
        return repo.findById(id).orElse(null);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public Propiedad crear(
            @RequestPart("propiedad") Propiedad propiedad,
            @RequestPart(value = "imagenes", required = false) MultipartFile[] imagenes,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen
    ) throws IOException {

        List<String> rutas = new ArrayList<>();
        MultipartFile[] archivos = normalizarImagenes(imagenes, imagen);
        propiedad.setId(new ObjectId().toHexString());

        if (archivos != null) {
            validarCantidadImagenes(archivos);
            int numeroImagen = 1;
            for (MultipartFile imagen1 : archivos) {
                if (!imagen1.isEmpty()) {
                    rutas.add("/api/imagenes/" + imageStorageService.store(
                            imagen1, propiedad.getId(), propiedad.getTitulo(), numeroImagen++));
                }
            }
        }

        propiedad.setImagenes(rutas);
        return repo.save(propiedad);
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public Propiedad actualizar(
            @PathVariable String id,
            @RequestPart("propiedad") Propiedad nueva,
            @RequestPart(value = "imagenes", required = false) MultipartFile[] imagenes,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen
    ) throws IOException {

        Propiedad existente = repo.findById(id).orElse(null);
        if (existente == null) return null;

        existente.setTitulo(nueva.getTitulo());
        existente.setDescripcion(nueva.getDescripcion());
        existente.setOperacion(nueva.getOperacion());
        existente.setTipo(nueva.getTipo());
        existente.setPrecio(nueva.getPrecio());
        existente.setWc(nueva.getWc());
        existente.setEstacionamiento(nueva.getEstacionamiento());
        existente.setHabitaciones(nueva.getHabitaciones());
        existente.setCiudad(nueva.getCiudad());
        existente.setBarrio(nueva.getBarrio());
        existente.setDireccion(nueva.getDireccion());
        existente.setMetrosCuadrados(nueva.getMetrosCuadrados());

        // Actualizar coordenadas si vienen en el request
        existente.setLatitud(nueva.getLatitud());
        existente.setLongitud(nueva.getLongitud());

        MultipartFile[] archivos = normalizarImagenes(imagenes, imagen);
        boolean reemplazarImagenes = archivos != null
                && Arrays.stream(archivos).anyMatch(archivo -> archivo != null && !archivo.isEmpty());
        List<String> imagenesAnteriores = existente.getImagenes();
        if (reemplazarImagenes) {
            validarCantidadImagenes(archivos);
            List<String> rutas = new ArrayList<>();
            int numeroImagen = 1;
            for (MultipartFile imagen1 : archivos) {
                if (imagen1 != null && !imagen1.isEmpty()) {
                    rutas.add("/api/imagenes/" + imageStorageService.store(
                            imagen1, existente.getId(), existente.getTitulo(), numeroImagen++));
                }
            }
            existente.setImagenes(rutas);
        }

        Propiedad guardada = repo.save(existente);
        if (reemplazarImagenes) {
            eliminarImagenes(imagenesAnteriores);
        }
        return guardada;
    }

    private MultipartFile[] normalizarImagenes(MultipartFile[] imagenes, MultipartFile imagen) {
        if (imagenes != null && imagenes.length > 0) return imagenes;
        if (imagen != null && !imagen.isEmpty()) return new MultipartFile[]{imagen};
        return null;
    }

    private void validarCantidadImagenes(MultipartFile[] archivos) {
        if (archivos.length > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se permiten hasta 4 imágenes por propiedad.");
        }
    }

    private void eliminarImagenes(List<String> imagenes) {
        if (imagenes == null) return;
        for (String imagen : imagenes) {
            imageStorageService.deleteByReference(imagen);
            if (imagen != null && imagen.startsWith("/uploads/")) {
                Path uploads = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
                Path archivo = uploads.resolve(imagen.substring("/uploads/".length())).normalize();
                if (archivo.startsWith(uploads)) {
                    try {
                        Files.deleteIfExists(archivo);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable String id) {
        Propiedad p = repo.findById(id).orElse(null);
        if (p != null) {
            eliminarImagenes(p.getImagenes());
            repo.deleteById(id);
        }
    }

    @PatchMapping("/{id}/visibilidad")
    public Propiedad cambiarVisibilidad(@PathVariable String id) {
        Propiedad p = repo.findById(id).orElse(null);
        if (p != null) {
            p.setVisible(!p.isVisible());
            repo.save(p);
        }
        return p;
    }

    @GetMapping("/visibles")
    public List<Propiedad> listarVisibles() {
        return repo.findAll()
                .stream()
                .filter(Propiedad::isVisible)
                .toList();
    }
}
package com.example.inmobiliaria.controller;

import org.springframework.http.CacheControl;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import com.example.inmobiliaria.Service.ImageStorageService;
import com.mongodb.client.gridfs.model.GridFSFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/imagenes")
@CrossOrigin(origins = "*")
public class ImagenController {

    private final ImageStorageService imageStorageService;

    public ImagenController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    //  Subir imagen
    @PostMapping("/subir")
    public ResponseEntity<String> subirImagen(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(imageStorageService.store(file));
    }

    //  Obtener imagen por ID
    @GetMapping("/{id}")
    public ResponseEntity<Resource> obtenerImagen(@PathVariable String id) throws IOException {
        GridFSFile file = imageStorageService.findById(id);

        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = imageStorageService.getResource(file);
        String contentType = file.getMetadata() != null && file.getMetadata().containsKey("_contentType")
            ? file.getMetadata().getString("_contentType")
            : null;
        if (!StringUtils.hasText(contentType)) {
            contentType = file.getFilename().toLowerCase().endsWith(".webp") ? "image/webp" : "image/jpeg";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}

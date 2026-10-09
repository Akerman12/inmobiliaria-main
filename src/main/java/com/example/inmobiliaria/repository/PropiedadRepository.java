package com.example.inmobiliaria.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.inmobiliaria.model.Propiedad;

public interface PropiedadRepository extends MongoRepository<Propiedad, String> {
    List<Propiedad> findByPropietarioId(String propietarioId);
}


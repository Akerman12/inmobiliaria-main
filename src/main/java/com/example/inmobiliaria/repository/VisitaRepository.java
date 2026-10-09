package com.example.inmobiliaria.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.inmobiliaria.model.Visita;

public interface VisitaRepository extends MongoRepository<Visita, String> {
    List<Visita> findByPropietarioIdOrderByFechaHoraAsc(String propietarioId);
    List<Visita> findByClienteIdOrderByFechaHoraAsc(String clienteId);
}
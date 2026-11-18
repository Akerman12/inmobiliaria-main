package com.example.inmobiliaria.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.inmobiliaria.model.DisponibilidadVisita;

public interface DisponibilidadVisitaRepository extends MongoRepository<DisponibilidadVisita, String> {
    List<DisponibilidadVisita> findByPropietarioIdAndFechaHoraAfterOrderByFechaHoraAsc(
            String propietarioId, LocalDateTime fechaHora);

    List<DisponibilidadVisita> findByPropietarioIdAndReservadaFalseAndFechaHoraAfterOrderByFechaHoraAsc(
            String propietarioId, LocalDateTime fechaHora);

    boolean existsByPropietarioIdAndFechaHora(String propietarioId, LocalDateTime fechaHora);
}

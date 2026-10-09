package com.example.inmobiliaria.repository;

import com.example.inmobiliaria.model.Contacto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContactoRepository extends MongoRepository<Contacto, String> {
	List<Contacto> findByCasaInteresInOrderByFechaCreacionDesc(List<String> casaInteresIds);
}

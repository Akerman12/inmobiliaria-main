package com.example.inmobiliaria.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.inmobiliaria.model.Testimonio;

public interface TestimonioRepository extends MongoRepository<Testimonio, String> {
}
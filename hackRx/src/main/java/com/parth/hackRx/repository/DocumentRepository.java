package com.parth.hackRx.repository;


import com.parth.hackRx.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
package com.swiftdrop.backend.modules.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    protected Courier() {
    }

    public Courier(UUID userId) {
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }
}
package edu.trk.start_project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// @Entity: клас відповідає таблиці; Hibernate сам її створить (ddl-auto=update)
@Entity
@Table(name = "personnel")
public class Person {

    // Первинний ключ, генерується самою БД (AUTO_INCREMENT у MySQL)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    // "rank" у MySQL 8 зарезервоване слово, тому колонка має інше ім'я
    @Column(name = "military_rank", length = 60)
    private String rank;

    @Column(length = 120)
    private String position;

    @Column(length = 60)
    private String unit;

    // present | leave | sick | mission | duty
    @Column(nullable = false, length = 20)
    private String status = "present";

    @Column(length = 500)
    private String note;

    private Instant statusChangedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Викликається автоматично перед першим збереженням
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        statusChangedAt = now;
    }

    // Викликається автоматично перед кожною зміною
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(Instant statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
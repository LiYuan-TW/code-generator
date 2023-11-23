package com.tw.common.seedwork;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@MappedSuperclass
@Getter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreationTimestamp
    @Column(updatable = false)
    protected Instant createdDate;

    @UpdateTimestamp
    protected Instant updatedDate;

    public void setId(Long id) {
        this.id = id;
    }
}

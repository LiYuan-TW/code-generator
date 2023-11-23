package com.tw.common.seedwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@MappedSuperclass
@Getter
@SuperBuilder
@RequiredArgsConstructor
public abstract class BaseDomainEntity implements Serializable {

    @JsonIgnore
    protected Long id;

    public void setId(Long id) {
        this.id = id;
    }

    @JsonIgnore
    protected boolean deleted;

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

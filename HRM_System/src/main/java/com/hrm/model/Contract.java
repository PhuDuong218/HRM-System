package com.hrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @Column(name = "contract_id")
    private Long contractId;
}

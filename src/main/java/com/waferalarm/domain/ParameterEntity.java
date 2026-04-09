package com.waferalarm.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parameter")
public class ParameterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String unit;
    private String description;

    @Column(name = "default_upper_limit")
    private Double defaultUpperLimit;

    @Column(name = "default_lower_limit")
    private Double defaultLowerLimit;

    protected ParameterEntity() {}

    public ParameterEntity(String name, String unit, Double defaultUpperLimit, Double defaultLowerLimit) {
        this.name = name;
        this.unit = unit;
        this.defaultUpperLimit = defaultUpperLimit;
        this.defaultLowerLimit = defaultLowerLimit;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public Double getDefaultUpperLimit() { return defaultUpperLimit; }
    public Double getDefaultLowerLimit() { return defaultLowerLimit; }
}

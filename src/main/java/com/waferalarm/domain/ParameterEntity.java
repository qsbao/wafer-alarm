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
    private String area;

    @Column(name = "default_upper_limit")
    private Double defaultUpperLimit;

    @Column(name = "default_lower_limit")
    private Double defaultLowerLimit;

    @Column(nullable = false)
    private boolean enabled = true;

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
    public String getDescription() { return description; }
    public String getArea() { return area; }
    public Double getDefaultUpperLimit() { return defaultUpperLimit; }
    public Double getDefaultLowerLimit() { return defaultLowerLimit; }
    public boolean isEnabled() { return enabled; }

    public void setName(String name) { this.name = name; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setDescription(String description) { this.description = description; }
    public void setArea(String area) { this.area = area; }
    public void setDefaultUpperLimit(Double defaultUpperLimit) { this.defaultUpperLimit = defaultUpperLimit; }
    public void setDefaultLowerLimit(Double defaultLowerLimit) { this.defaultLowerLimit = defaultLowerLimit; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

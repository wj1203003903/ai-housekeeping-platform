package com.anxin.home.vets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "service_items")
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne
    @JoinColumn(name = "skill_id")
    private ServiceSkill skill;

    @Column(name = "pricing_type")
    private String pricingType;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "minimum_units")
    private BigDecimal minimumUnits;

    @Column(name = "estimated_minutes")
    private int estimatedMinutes;

    @Column(name = "enabled")
    private boolean enabled;

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ServiceSkill getSkill() {
        return skill;
    }

    public String getPricingType() {
        return pricingType;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getMinimumUnits() {
        return minimumUnits;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

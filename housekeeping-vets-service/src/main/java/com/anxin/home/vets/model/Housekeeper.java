package com.anxin.home.vets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "housekeepers")
public class Housekeeper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "name")
    private String name;

    @NotBlank
    @Column(name = "telephone")
    private String telephone;

    @Column(name = "introduction")
    private String introduction;

    @Column(name = "experience_years")
    private int experienceYears;

    @Column(name = "rating")
    private BigDecimal rating;

    @Column(name = "status")
    private String status = "AVAILABLE";

    @Column(name = "service_district")
    private String serviceDistrict;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "housekeeper_skills",
        joinColumns = @JoinColumn(name = "housekeeper_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<ServiceSkill> skills = new LinkedHashSet<>();

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getServiceDistrict() {
        return serviceDistrict;
    }

    public void setServiceDistrict(String serviceDistrict) {
        this.serviceDistrict = serviceDistrict;
    }

    public Set<ServiceSkill> getSkills() {
        return skills;
    }
}

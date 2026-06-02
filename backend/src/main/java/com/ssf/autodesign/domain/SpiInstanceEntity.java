package com.ssf.autodesign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "spi_instances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_spi_workspace_instance", columnNames = {"workspace_id", "instance_id"})
})
public class SpiInstanceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private WorkspacePathEntity workspace;

    @Column(name = "instance_id", nullable = false, length = 80)
    private String instanceCode;

    @Column(nullable = false, length = 200)
    private String instanceName;

    @Column(length = 200)
    private String productName;

    @Column(length = 80)
    private String currentPhase;

    @Column(length = 80)
    private String status;

    @Column(nullable = false, length = 1200)
    private String rootPath;

    @Column(length = 80)
    private String manifestUpdatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public WorkspacePathEntity getWorkspace() {
        return workspace;
    }

    public void setWorkspace(WorkspacePathEntity workspace) {
        this.workspace = workspace;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getManifestUpdatedAt() {
        return manifestUpdatedAt;
    }

    public void setManifestUpdatedAt(String manifestUpdatedAt) {
        this.manifestUpdatedAt = manifestUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
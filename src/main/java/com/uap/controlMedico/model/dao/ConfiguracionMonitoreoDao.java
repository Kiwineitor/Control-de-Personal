package com.uap.controlMedico.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.ConfiguracionMonitoreo;

public interface ConfiguracionMonitoreoDao extends JpaRepository<ConfiguracionMonitoreo, Long> {
}

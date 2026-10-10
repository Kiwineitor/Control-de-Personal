package com.uap.controlMedico.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import com.uap.controlMedico.model.entity.PlantillaTelegram;
import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

public interface PlantillaTelegramDao extends JpaRepository<PlantillaTelegram, TipoMensajeTelegram> {}

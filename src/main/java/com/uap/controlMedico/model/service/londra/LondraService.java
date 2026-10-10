package com.uap.controlMedico.model.service.londra;

import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.Titular;

public interface LondraService {
    void sincronizarTitular(Titular titular);
    void sincronizarInterno(Interno interno);

}

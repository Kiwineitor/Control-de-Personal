package com.uap.controlMedico.model.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonaDTO {
    private int perId;
    private String ci;
    private String nombres;
    private String apPaterno;
    private String apMaterno;
    private String email;

    public PersonaDTO() {
    }

    public String getNombreCompleto(){
        if(this.getApPaterno() == null){
            return this.getNombres()+" "+this.getApPaterno();
        }

        if(this.getNombres() == null){
            return this.getNombres()+" "+this.getApMaterno();
        }

        return this.getNombres()+" "+this.getApPaterno()+" "+this.getApMaterno();
    }

    public String getNombreCompletoFormal(){
        if(this.getApPaterno() == null){
            return this.getNombres()+" "+this.getApPaterno();
        }

        if(this.getNombres() == null){
            return this.getNombres()+" "+this.getApMaterno();
        }

        return this.getApPaterno()+" "+this.getApMaterno()+ " "+this.getNombres();
    }

    public String getNombreCompletoFuncionario(){
        if(this.getApPaterno() == null){
            return this.getNombres()+" "+this.getApPaterno();
        }

        if(this.getNombres() == null){
            return this.getNombres()+" "+this.getApMaterno();
        }

        return this.getApPaterno()+" "+this.getApMaterno()+ " "+this.getNombres() + " - " +this.getPerId();
    }

}

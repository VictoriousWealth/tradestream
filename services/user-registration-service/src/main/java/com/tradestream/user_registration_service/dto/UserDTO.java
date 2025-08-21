package com.tradestream.user_registration_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {

    @NotBlank @Size(min = 3, max = 255)
    private String username;
    @NotBlank @Size(min = 6, max = 255)
    private String password;

}

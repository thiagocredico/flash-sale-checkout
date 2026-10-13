package br.com.ada.flashsale.checkout.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String mensagem, List<String> erros) {

    public static ApiError message(String message) {
        return new ApiError(message, null);
    }

    public static ApiError validation(List<String> errors) {
        return new ApiError("Dados inválidos", errors);
    }
}
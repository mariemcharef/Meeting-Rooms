package com.nordia.rooms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull Integer roomId,
        @NotBlank String start,
        @NotBlank String end,
        @NotBlank @Size(max = 100) String organiser,
        @NotBlank @Size(max = 200) String subject) {
}

package edu.trk.start_project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// record: компактний незмінний клас лише з даними
public record PersonRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 60) String rank,
        @Size(max = 120) String position,
        @Size(max = 60) String unit,
        // Якщо статус передано, він має бути одним із п'яти значень
        @Pattern(regexp = "present|leave|sick|mission|duty",
                message = "status must be one of: present, leave, sick, mission, duty") String status,
        @Size(max = 500) String note) {
}
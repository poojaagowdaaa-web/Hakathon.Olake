
package Hakathon.Slack.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "question must not be empty")
        @Size(max = 1000, message = "question is too long")
        String question) {
}
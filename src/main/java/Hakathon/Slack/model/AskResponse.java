package Hakathon.Slack.model;

import java.util.List;

public record AskResponse(String answer, List<String> sources) {
}
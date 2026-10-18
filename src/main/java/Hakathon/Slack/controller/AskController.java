package Hakathon.Slack.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import Hakathon.Slack.model.AskRequest;
import Hakathon.Slack.model.AskResponse;
import Hakathon.Slack.service.KnowledgeBase;
import Hakathon.Slack.service.KnowledgeBase.ScoredChunk;
import jakarta.validation.Valid;

@RestController
public class AskController {

    private static final String NOT_FOUND =
            "I couldn't find this in the OLake docs. Please try rephrasing your question, "
            + "or ask the OLake team through their community channels (Slack).";

    private final KnowledgeBase kb;

    @Value("${ask.min-score:4.0}")
    private double minScore;

    @Value("${ask.min-coverage:0.5}")
    private double minCoverage;

    @Value("${ask.relative-cutoff:0.85}")
    private double relativeCutoff;

    public AskController(KnowledgeBase kb) {
        this.kb = kb;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        List<ScoredChunk> top = kb.searchScored(request.question(), 3);

        if (top.isEmpty()) {
            System.out.println("Question: " + request.question() + " | no matches");
            return new AskResponse(NOT_FOUND, List.of());
        }

        ScoredChunk first = top.get(0);
        System.out.println("Question: " + request.question()
                + " | best score: " + first.score() + " | coverage: " + first.coverage());

        if (first.score() < minScore || first.coverage() < minCoverage) {
            return new AskResponse(NOT_FOUND, List.of());
        }

        double cutoff = first.score() * relativeCutoff;
        StringBuilder sb = new StringBuilder();
        List<String> sources = new ArrayList<>();
        for (ScoredChunk sc : top) {
            if (sc.score() < cutoff || sc.coverage() < minCoverage) {
                continue;
            }
            sb.append(sc.chunk().text()).append("\n\n");
            if (!sources.contains(sc.chunk().url())) {
                sources.add(sc.chunk().url());
            }
        }
        return new AskResponse(sb.toString().trim(), sources);
    }
}
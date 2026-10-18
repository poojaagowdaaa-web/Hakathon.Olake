package Hakathon.Slack.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import Hakathon.Slack.model.Chunk;
import jakarta.annotation.PostConstruct;

@Service
public class KnowledgeBase {

    /** A chunk, its score, and how many of the question's words it contains (0 to 1). */
    public record ScoredChunk(Chunk chunk, double score, double coverage) {
    }

    private static final Set<String> STOP = Set.of("the", "a", "an", "is", "are", "of", "to", "in", "on",
            "for", "and", "or", "do", "does", "can", "i", "my", "what", "how", "which", "with", "it", "me", "by");

    private final List<Chunk> chunks = new ArrayList<>();
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private double avgLen = 1;

    @PostConstruct
    public void load() throws IOException {
        readFile("keyfacts.txt", "||", true);
        readFile("chunks.tsv", "\t", false);
        long total = 0;
        for (int len : lengths) {
            total += len;
        }
        avgLen = chunks.isEmpty() ? 1 : (double) total / chunks.size();
        System.out.println("KnowledgeBase loaded " + chunks.size() + " chunks");
    }

    private void readFile(String name, String sep, boolean boost) throws IOException {
        ClassPathResource res = new ClassPathResource(name);
        if (!res.exists()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf(sep);
                if (i < 0) {
                    continue;
                }
                String url = line.substring(0, i).trim();
                String text = line.substring(i + sep.length()).replace("\\n", "\n").trim();
                add(new Chunk(url, text), boost);
            }
        }
    }

    private void add(Chunk c, boolean boost) {
        chunks.add(c);
        Map<String, Integer> tf = new HashMap<>();
        List<String> tokens = tokenize(c.text());
        int weight = boost ? 2 : 1;
        for (String t : tokens) {
            tf.merge(t, weight, Integer::sum);
        }
        for (String t : tf.keySet()) {
            docFreq.merge(t, 1, Integer::sum);
        }
        termFreqs.add(tf);
        lengths.add(tokens.size());
    }

    private List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String t : text.toLowerCase().split("[^a-z0-9_]+")) {
            if (t.length() > 1 && !STOP.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Old method, kept so nothing else breaks. */
    public List<Chunk> search(String query, int k) {
        List<Chunk> result = new ArrayList<>();
        for (ScoredChunk sc : searchScored(query, k)) {
            result.add(sc.chunk());
        }
        return result;
    }

    /** Finds the best chunks and tells how well each one matches. */
    public List<ScoredChunk> searchScored(String query, int k) {
        List<String> q = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        int n = chunks.size();
        double[] scores = new double[n];
        double[] cover = new double[n];
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            double len = lengths.get(i);
            double s = 0;
            int matched = 0;
            for (String term : q) {
                Integer f = tf.get(term);
                if (f == null) {
                    continue;
                }
                matched++;
                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                s += idf * (f * 2.2) / (f + 1.2 * (0.25 + 0.75 * len / avgLen));
            }
            scores[i] = s;
            cover[i] = q.isEmpty() ? 0 : (double) matched / q.size();
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (scores[i] > 0) {
                idx.add(i);
            }
        }
        idx.sort((a, b) -> Double.compare(scores[b], scores[a]));
        List<ScoredChunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, idx.size()); i++) {
            int id = idx.get(i);
            result.add(new ScoredChunk(chunks.get(id), scores[id], cover[id]));
        }
        return result;
    }
}
package Hakathon.Slack.scraper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import Hakathon.Slack.model.Chunk;

public class DocScraper {

    private static final int MAX_CHARS = 900;

    public static void main(String[] args) throws Exception {
        List<String> urls = Files.readAllLines(Path.of("urls.txt"));
        List<String> lines = new ArrayList<>();
        for (String url : urls) {
            url = url.trim();
            if (url.isEmpty()) {
                continue;
            }
            try {
                Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(20000).get();
                List<Chunk> chunks = toChunks(url, doc);
                for (Chunk c : chunks) {
                    String text = c.text().replace("\t", " ").replace("\r", "").replace("\n", "\\n");
                    lines.add(c.url() + "\t" + text);
                }
                System.out.println("OK   " + chunks.size() + "  " + url);
            } catch (Exception e) {
                System.out.println("FAIL " + url + " -> " + e.getMessage());
            }
        }
        Files.write(Path.of("src/main/resources/chunks.tsv"), lines, StandardCharsets.UTF_8);
        System.out.println("Saved " + lines.size() + " chunks");
    }

    private static List<Chunk> toChunks(String url, Document doc) {
        Element root = doc.selectFirst("article");
        if (root == null) {
            root = doc.body();
        }
        String title = doc.title();
        String heading = "";
        StringBuilder buf = new StringBuilder();
        List<Chunk> out = new ArrayList<>();

        for (Element el : root.select("h1, h2, h3, h4, p, li, pre, tr")) {
            String tag = el.tagName();
            if (tag.startsWith("h")) {
                flush(out, url, title, heading, buf);
                heading = el.text();
                continue;
            }
            String text;
            if (tag.equals("tr")) {
                text = String.join(" | ", el.select("th, td").eachText());
            } else if (el.closest("td, th") != null) {
                continue;
            } else if (tag.equals("pre")) {
                text = el.wholeText().trim();
            } else if (tag.equals("p") && el.closest("li") != null) {
                continue;
            } else {
                text = el.text();
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            buf.append(text).append("\n");
            if (buf.length() > MAX_CHARS) {
                flush(out, url, title, heading, buf);
            }
        }
        flush(out, url, title, heading, buf);
        return out;
    }

    private static void flush(List<Chunk> out, String url, String title, String heading, StringBuilder buf) {
        if (buf.length() > 40) {
            out.add(new Chunk(url, title + " > " + heading + "\n" + buf.toString().trim()));
        }
        buf.setLength(0);
    }
}
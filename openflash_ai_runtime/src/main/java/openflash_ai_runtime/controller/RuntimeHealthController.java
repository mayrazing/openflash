package openflash_ai_runtime.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RuntimeHealthController {

    private static final String STARTED_PAGE = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OpenFlash AI Runtime</title>
          <style>
            body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid;
              place-items: center; background: #f8fafc; color: #0f172a; }
            main { text-align: center; padding: 2rem; }
            h1 { margin: 0 0 0.75rem; }
            p { margin: 0.35rem 0; color: #475569; }
            strong { color: #15803d; }
            @media (prefers-color-scheme: dark) {
              body { background: #0f172a; color: #f8fafc; }
              p { color: #cbd5e1; }
              strong { color: #4ade80; }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>OpenFlash AI Runtime</h1>
            <p>Service started successfully.</p>
            <p>Status: <strong>UP</strong></p>
          </main>
        </body>
        </html>
        """;

    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String startedPage() {
        return STARTED_PAGE;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}

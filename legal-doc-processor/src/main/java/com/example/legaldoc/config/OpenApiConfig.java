package com.example.legaldoc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires springdoc-openapi. UI is served at:
 * <ul>
 *   <li>{@code GET /swagger-ui.html} — interactive UI</li>
 *   <li>{@code GET /v3/api-docs} — raw OpenAPI 3 JSON</li>
 * </ul>
 * Every controller is picked up automatically; the {@code @Operation} /
 * {@code @ApiResponse} annotations on the controllers and the {@code @Schema}
 * annotations on the models drive the generated docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI legalDocOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Legal Document Processor API")
                        .description("""
                                Upload legal documents for per-page text and image extraction,
                                TF-IDF keyword scoring, and Elasticsearch indexing. Pipeline:
                                S3 upload → SQS → Temporal workflow (plan → page fan-out → manifest → index).
                                Per-page artifacts are staged in the S3 tmp area and described by a
                                manifest so downstream consumers can reassemble the document without
                                re-running extraction.""")
                        .version("1.0.0")
                        .contact(new Contact().name("Legal Doc Platform").email("platform@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}

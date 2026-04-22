# Changes to legal-doc-processor

Summary of the edits applied against the original zip. Assume the original
source was the baseline.

## New dependencies (`pom.xml`)

A `pom.xml` was added (the original zip had none). Target: Spring Boot 3.3.5 on
Java 21. Beyond what the existing code already used, new deps:

- `org.apache.pdfbox:pdfbox:3.0.3` — per-page text + image extraction, text→PDF
- `org.apache.poi:poi-ooxml:5.3.0` — DOCX page-break detection + picture pool
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` — Swagger UI
- `jackson-datatype-jsr310` — required by the ES Jackson-mapper fix
- `io.temporal:temporal-testing` — used by `DocumentProcessingWorkflowTest`

## Per-page extraction

Three new extractors under `service/extract/`:

- `PdfPageExtractor` — streams pages via PDFBox 3's `Loader.loadPDF`,
  `PDFTextStripper.setStartPage/setEndPage`. Walks `PDResources` for
  `PDImageXObject` on each page; recurses into `PDFormXObject` so images nested
  inside form XObjects aren't missed. Normalizes every image to PNG via
  `ImageIO`. Exposes `extractPages(bytes, Consumer)`, `extractSinglePage(bytes, N)`,
  and `getPageCount(bytes)`.
- `DocxPageExtractor` — splits on explicit `<w:br w:type="page"/>`. If the
  document contains none, falls back to chunking paragraphs in groups of 40.
  Extracts `XWPFDocument.getAllPictures()` and attaches them to page 1
  (DOCX pictures aren't intrinsically tied to page position).
- `TextToPdfConverter` — renders a `String` into LETTER-sized PDF pages with
  Courier 10pt, hard-wrapping long lines to 95 chars. Replaces non-WinAnsi
  characters with `?` so the converter never throws on CJK / symbol inputs.

The router `DocumentPageExtractor` picks the path by content type:

| Input                                 | Route                                                    |
| ------------------------------------- | -------------------------------------------------------- |
| `application/pdf`                     | `PdfPageExtractor` directly                              |
| DOCX (`.docx` or the OOXML mime type) | `DocxPageExtractor`                                      |
| `text/*` > 512 KB                     | `TextToPdfConverter` → `PdfPageExtractor`                |
| `text/*` ≤ 512 KB                     | One synthetic page, no conversion                        |
| Anything else                         | One synthetic empty page (manifest always gets produced) |

The 512 KB threshold is exposed as `DocumentPageExtractor.LARGE_TEXT_THRESHOLD_BYTES`.

## S3 tmp layout

`PageStorageService` owns the on-disk layout and writes the manifest:

```
tmp/{documentId}/{sanitized_filename}/
    pages/page-0001.txt       (UTF-8, zero-padded so lex order = page order)
    pages/page-0002.txt
    images/page-0001-img-01.png
    images/page-0001-img-02.jpg
    manifest.json             (DocumentPageManifest — see below)
```

The manifest lists every page's text S3 key and per-page image keys, so a
downstream consumer can reassemble the document by reading page keys in order
without re-running extraction.

If the source was normalized (large text rendered to PDF), the normalized PDF
is also uploaded to `tmp/{documentId}/{file}/source.pdf` so page activities
can extract from it without re-running the conversion each time.

## Two-worker Temporal setup

Workflow split into two task queues:

- **`legal-doc-task-queue`** (orchestration): runs the workflow itself and
  three light activities — `planDocument`, `finalizeManifest`, `indexDocument`.
- **`legal-doc-page-queue`** (page work): runs the single heavy activity
  `extractAndStorePage`, registered on a second worker.

`DocumentProcessingWorkflowImpl` fans out one `extractAndStorePage` call per
page via `Async.function` + `Promise.allOf`. Pages may complete out of order;
the workflow sorts `PageRef`s by page number before building the manifest.

`TemporalConfig` registers both workers on a shared `WorkerFactory` and starts
it after both are registered. In a real deployment you'd typically run the
page worker in a separate JVM/pod; running both in-process is sufficient for
local dev and the test environment.

## Elasticsearch indexing fix

Two changes in `service/ElasticsearchService.java` and
`config/ElasticsearchConfig.java`:

1. **The bug.** The previous `JacksonJsonpMapper()` was constructed with no
   arguments, which installs a vanilla `ObjectMapper` — no `JavaTimeModule`.
   That meant `DocumentMetadata.uploadDateTime` (an `Instant`) serialized as
   a numeric epoch, which the `date`-typed field in the index mapping rejects
   with a `mapper_parsing_exception`. Fix: build an `ObjectMapper` with
   `JavaTimeModule` registered and `WRITE_DATES_AS_TIMESTAMPS` disabled, then
   pass it to `JacksonJsonpMapper(mapper)`. Now `Instant`s serialize as
   `"2025-04-22T14:05:00Z"`, which ES accepts by default.
   `ElasticsearchJacksonMapperTest` pins this down.
2. **Refresh.** `indexDocument` now sets `refresh=Refresh.True` so writes are
   immediately visible to subsequent searches. Without this, tests (and naïve
   callers) that search right after indexing hit ES's default 1-second
   refresh interval and see zero hits.

The index mapping also gained `manifestS3Key` and `tmpPrefix` (both `keyword`)
so searches can filter on paginated-vs-non-paginated or locate the manifest
without a follow-up lookup.

## Swagger / OpenAPI

- `OpenApiConfig` configures the title, description, version, and contact info.
- Controllers (`UploadController`, `SearchController`, `HealthController`) are
  annotated with `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`.
- Models (`DocumentMetadata`, `DocumentPageManifest`, `PageRef`,
  `ServiceHealthStatus`) are annotated with `@Schema`.

After startup the UI is at `GET /swagger-ui.html` and the raw spec at
`GET /v3/api-docs`.

## Tests

Six new test files (the five originals are untouched):

- `PdfPageExtractorTest` — builds an in-memory multi-page PDF, verifies
  streaming order, single-page access, and out-of-range rejection.
- `DocxPageExtractorTest` — exercises both the explicit-page-break path and
  the paragraph-chunking fallback, plus the empty-document edge case.
- `TextToPdfConverterTest` — converts short and large text, round-trips
  through `PdfPageExtractor`, and confirms non-WinAnsi input doesn't crash.
- `DocumentPageExtractorTest` — pins the router decisions for all four
  branches including the >512 KB text→PDF route.
- `PageStorageServiceTest` — mocks `S3Service` and asserts exact S3 keys
  written for text, images, and the manifest (including ISO-8601 timestamp).
- `ElasticsearchJacksonMapperTest` — demonstrates the default mapper is
  broken for `Instant` and the configured mapper produces the expected
  ISO-8601 string.
- `DocumentProcessingWorkflowTest` — uses `TestWorkflowEnvironment` with both
  task queues and mocked activities to verify fan-out, ordering, and call
  sequence.

## Not done / known risks

- **Never compiled.** Maven Central is blocked by the egress proxy in this
  sandbox (403 Forbidden), so `mvn compile` can't resolve dependencies. Run
  `mvn test` locally to verify.
- **Source re-download per page.** `extractAndStorePage` downloads the
  normalized source from S3 for every page. For a 100-page PDF that's 100
  downloads. An activity-process byte cache keyed by `extractionSourceS3Key`
  would fix it; I did not add one.
- **JBIG2 / JPEG2000 PDF images** need the `jbig2-imageio` and
  `jai-imageio-jpeg2000` jars that are not in the pom. The extractor logs a
  warning and skips those images rather than crashing.
- **`TextExtractionService` is now dead code.** Nothing on the workflow path
  calls it. Its unit test still runs, so it's kept in place.
- **No backpressure on page fan-out.** A 1000-page PDF will submit 1000
  activities at once. Temporal handles that fine, but the page worker will
  process them at whatever concurrency you configure (200 by default).

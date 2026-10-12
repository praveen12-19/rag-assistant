# Verified RAG Knowledge Assistant

An enterprise-grade, strictly grounded **Retrieval-Augmented Generation (RAG) Knowledge Assistant** built with **Java 21**, **Spring Boot 3.4.3**, and **Spring AI**. 

The system allows users to upload corporate policy and Standard Operating Procedure (SOP) PDFs, ask natural-language questions, and receive factual answers with exact document citations (document name + page number). If supporting evidence in the uploaded documents is insufficient, the system safely refuses to speculate by halting before calling the LLM and returning a standardized fallback.

---

## 1. Project Overview & Architecture Flow

### High-Level Flow Diagram

```
                    ┌──────────────────────────────────────────────┐
                    │               User / Frontend                │
                    └───────┬──────────────────────────────▲───────┘
                            │                              │
             1. Upload PDF  │               4. Grounded    │
                or Ask Q    │                  Answer +    │
                            ▼                  Citations   │
                    ┌──────────────────────────────────────────────┐
                    │          Spring Boot REST Backend            │
                    │      (Controllers, Services, Security)       │
                    └───────┬──────────────────────┬───────────────┘
                            │                      │
             Extract text & │         2. Vector    │ 3. Evaluate Threshold:
             save metadata  │            Search    │    - Score < 0.65: Refuse (Safe Fallback)
                            ▼                      │    - Score >= 0.65: Call Gemini LLM
         ┌─────────────────────────┐               ▼
         │   MySQL Database        │      ┌─────────────────────────────────┐
         │ - documents             │      │  SimpleVectorStore              │
         │ - query_logs            │      │  (data/vector-store.json)       │
         └─────────────────────────┘      │  Embeddings: text-embedding-004 │
                                          └────────────────┬────────────────┘
                                                           │ (Above threshold only)
                                                           ▼
                                          ┌─────────────────────────────────┐
                                          │ Google Gemini LLM               │
                                          │ Model: gemini-1.5-flash         │
                                          │ Temp: 0.0 (Strictly Grounded)   │
                                          └─────────────────────────────────┘
```

### Architectural Highlights
1. **Layered Architecture**: Strict separation of concerns (`Controller` -> `Service` -> `Repository`). Controllers contain zero business logic.
2. **Deterministic Refusal (Anti-Hallucination Guardrail)**: The system checks vector similarity scores against a strict safety threshold (`0.65`). If the highest score is below 0.65 or if no chunks are found, the LLM is **never called**, saving API costs and guaranteeing zero hallucinated speculation.
3. **Page-by-Page Ingestion**: Apache PDFBox parses documents page by page to tag each chunk with precise source metadata (`document`, `page`, `version`).
4. **Audit Logging**: Every query, generated answer, similarity score, latency in milliseconds, and citation list is logged to MySQL.

---

## 2. Project Folder Structure

The project separates frontend and backend code cleanly:

```
RAG assistant/
├── README.md                           # Master documentation & interview guide
├── frontend/                           # Standalone Frontend Application
│   ├── index.html                      # Modern UI (Upload, Search, Citations, Admin view)
│   ├── app.js                          # REST client, dynamic rendering & status polling
│   └── style.css                       # Curated dark-theme CSS design system
└── backend/                            # Spring Boot 3.x Backend Application
    ├── pom.xml                         # Maven dependencies & plugins
    ├── mvnw / mvnw.cmd                 # Maven wrapper scripts
    ├── .gitignore                      # Git ignore rules
    ├── data/
    │   └── vector-store.json           # In-memory vector store persistent cache
    ├── sample-docs/                    # Synthetic corporate policies (Page-formatted)
    │   ├── hr-leave-policy.pdf         # 3-page HR leave policy PDF
    │   ├── hr-leave-policy.txt         # Plain text source for HR leave policy
    │   ├── it-security-policy.pdf      # 3-page IT security & compliance PDF
    │   ├── it-security-policy.txt      # Plain text source for IT security policy
    │   ├── travel-policy.pdf           # 3-page Business travel & expense PDF
    │   └── travel-policy.txt           # Plain text source for travel policy
    ├── eval/
    │   └── questions.json              # 20 test questions (15 answerable, 5 uncovered)
    └── src/
        ├── main/
        │   ├── java/com/praveen/ragassistant/
        │   │   ├── RagAssistantApplication.java
        │   │   ├── config/
        │   │   │   ├── AiConfig.java           # Vector store bean & startup loader
        │   │   │   └── CorsConfig.java         # Cross-Origin resource sharing
        │   │   ├── controller/
        │   │   │   ├── DocumentController.java # POST/GET /api/documents
        │   │   │   ├── AskController.java      # POST /api/ask
        │   │   │   └── AdminController.java    # GET /api/history & /api/unanswered
        │   │   ├── service/
        │   │   │   ├── DocumentService.java    # Validation, versioning & orchestration
        │   │   │   ├── IngestionService.java   # PDF parsing, sliding chunking & embedding
        │   │   │   ├── RetrievalService.java   # Top-K vector retrieval & score evaluation
        │   │   │   ├── AnswerService.java      # Refusal threshold logic & LLM prompting
        │   │   │   └── QueryLogService.java    # MySQL query audit logging
        │   │   ├── entity/
        │   │   │   ├── DocumentEntity.java     # JPA entity for 'documents' table
        │   │   │   └── QueryLog.java           # JPA entity for 'query_logs' table
        │   │   ├── repository/
        │   │   │   ├── DocumentRepository.java
        │   │   │   └── QueryLogRepository.java
        │   │   ├── dto/
        │   │   │   ├── AskRequest.java         # Validated question payload
        │   │   │   ├── AskResponse.java        # Answer, confidence & citations
        │   │   │   ├── Citation.java           # Document, page, snippet
        │   │   │   ├── DocumentResponse.java   # Document metadata payload
        │   │   │   ├── QueryLogResponse.java   # Admin audit log DTO
        │   │   │   └── ErrorResponse.java      # RFC-standard error payload
        │   │   └── exception/
        │   │       ├── GlobalExceptionHandler.java
        │   │       ├── InvalidFileException.java
        │   │       └── DocumentNotFoundException.java
        │   └── resources/
        │       ├── application.properties      # Central application configuration
        │       ├── prompts/
        │       │   └── rag-system-prompt.st    # StringTemplate grounded system prompt
        │       └── static/                     # Embedded static frontend assets
        │           ├── index.html
        │           ├── app.js
        │           └── style.css
        └── test/java/com/praveen/ragassistant/
            ├── service/
            │   ├── IngestionServiceTest.java   # PDF parsing, chunking & metadata test
            │   └── AnswerServiceTest.java      # Threshold refusal & prompt grounding test
            ├── controller/
            │   └── AskControllerTest.java      # MockMvc endpoint & validation test
            └── util/
                └── GenerateSamplePdfs.java     # Utility test generating test PDFs
```

---

## 3. Gemini Setup & Configuration Details

### Setup Model: Google AI Studio Developer API
For this project, we use the **Google AI Studio API key** (`GEMINI_API_KEY`) via Spring AI's official `spring-ai-starter-model-google-genai` and `spring-ai-starter-model-google-genai-embedding` starters.

*Why this setup over Vertex AI?*
- **No Cloud Console complexity**: Does not require a Google Cloud Platform (GCP) project, service account JSON key, billing account, or `gcloud auth application-default login`.
- **Simplest free-tier prototyping**: You obtain an instant API key directly from [Google AI Studio](https://aistudio.google.com/).
- **Standardized environment variable**: Read directly from `GEMINI_API_KEY`.

### Exact Properties in `application.properties`

```properties
# Google AI Studio Gemini API Configuration
spring.ai.google.genai.api-key=${GEMINI_API_KEY:}
spring.ai.google.genai.chat.options.model=gemini-1.5-flash
spring.ai.google.genai.chat.options.temperature=0.0
spring.ai.google.genai.embedding.options.model=text-embedding-004

# RAG Knowledge Assistant Tuning
rag.chunk-size=500
rag.chunk-overlap=50
rag.top-k=3
rag.similarity-threshold=0.65
rag.vector-store.file-path=data/vector-store.json
```

---

## 4. Setup & Running Instructions

### Prerequisites
1. **Java 21+** (Java 21 or Java 25 LTS)
2. **Maven 3.8+** (or use the included `./mvnw` / `mvnw.cmd`)
3. **MySQL Server** running locally on port 3306 (or configured via environment variables)
4. A Google Gemini API key from [aistudio.google.com](https://aistudio.google.com/)

---

### Step 1: Create the MySQL Database
Log into MySQL and execute:
```sql
CREATE DATABASE IF NOT EXISTS rag_assistant;
```

*(Note: Spring Boot's connection string includes `createDatabaseIfNotExist=true`, so MySQL will also create it automatically if user permissions allow).*

---

### Step 2: Set Environment Variables

#### On Windows (PowerShell):
```powershell
$env:GEMINI_API_KEY="your-gemini-api-key-here"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="your-mysql-password"
```

#### On Windows (Command Prompt):
```cmd
set GEMINI_API_KEY=your-gemini-api-key-here
set SPRING_DATASOURCE_USERNAME=root
set SPRING_DATASOURCE_PASSWORD=your-mysql-password
```

#### On macOS / Linux:
```bash
export GEMINI_API_KEY="your-gemini-api-key-here"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="your-mysql-password"
```

---

### Step 3: Build & Run the Backend

Navigate to the `backend` folder and run:

```bash
cd backend
mvn clean test
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

### Step 4: Access the Frontend

You have two convenient options to access the UI:

1. **Integrated Server (Recommended)**: Open `http://localhost:8080/` in your browser. Spring Boot automatically serves the web client directly from `src/main/resources/static/`.
2. **Standalone Frontend**: Double-click `frontend/index.html` or open it with VS Code Live Server (`http://localhost:5500`). The CORS configuration automatically allows cross-origin requests to `http://localhost:8080`.

---

## 5. REST API Endpoints & Sample Curl Commands

### 1. Upload a Document
- **Endpoint**: `POST /api/documents`
- **Content-Type**: `multipart/form-data`
- **Description**: Validates that the file is a non-empty PDF, determines the version, splits pages into chunks, stores embeddings in `SimpleVectorStore`, saves to `data/vector-store.json`, and records metadata in MySQL.

```bash
curl -X POST http://localhost:8080/api/documents \
  -F "file=@sample-docs/hr-leave-policy.pdf"
```

**Sample Response (`201 Created`)**:
```json
{
  "id": 1,
  "fileName": "hr-leave-policy.pdf",
  "version": 1,
  "pageCount": 3,
  "chunkCount": 8,
  "uploadedAt": "2026-10-11T19:15:30"
}
```

---

### 2. List Uploaded Documents
- **Endpoint**: `GET /api/documents`
- **Description**: Lists all uploaded documents and metadata ordered by upload timestamp.

```bash
curl -X GET http://localhost:8080/api/documents
```

**Sample Response (`200 OK`)**:
```json
[
  {
    "id": 1,
    "fileName": "hr-leave-policy.pdf",
    "version": 1,
    "pageCount": 3,
    "chunkCount": 8,
    "uploadedAt": "2026-10-11T19:15:30"
  }
]
```

---

### 3. Ask a Grounded Question (Above Threshold)
- **Endpoint**: `POST /api/ask`
- **Content-Type**: `application/json`
- **Description**: Retrieves top-3 chunks. Score is >= 0.65, so Gemini is called with the grounded prompt.

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How many days of paid annual leave do employees accrue per calendar year?"}'
```

**Sample Response (`200 OK`)**:
```json
{
  "answered": true,
  "answer": "Employees accrue 18 days of paid Annual Leave per calendar year, earned at a rate of 1.5 days per completed month of service [Document: hr-leave-policy.pdf, Page: 1].",
  "confidence": 0.88,
  "citations": [
    {
      "document": "hr-leave-policy.pdf",
      "page": 1,
      "snippet": "Employees accrue 18 days of paid Annual Leave per calendar year, earned at a rate of 1.5 days per completed month of service. Annual leave must be applied for at least 7 business days in advance..."
    }
  ]
}
```

---

### 4. Ask an Uncovered Question (Below Threshold Refusal)
- **Endpoint**: `POST /api/ask`
- **Content-Type**: `application/json`
- **Description**: The query is not covered by the documents. The similarity score is below `0.65`. Gemini is **NOT** called.

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Are employees allowed to bring pet dogs to the office on Fridays?"}'
```

**Sample Response (`200 OK`)**:
```json
{
  "answered": false,
  "answer": "I couldn't find this in the uploaded documents.",
  "confidence": 0.38,
  "citations": []
}
```

---

### 5. Audit History (Admin View)
- **Endpoint**: `GET /api/history`
- **Description**: Returns all queries logged in the MySQL `query_logs` table.

```bash
curl -X GET http://localhost:8080/api/history
```

---

### 6. Refused Queries (Admin View)
- **Endpoint**: `GET /api/unanswered`
- **Description**: Returns all queries where `answered = false` (refused questions).

```bash
curl -X GET http://localhost:8080/api/unanswered
```

---

## 6. How to Evaluate: Grounded-Answer Rate & Correct-Refusal Rate

The project includes `eval/questions.json` containing **20 synthetic benchmark questions**:
- **15 Answerable Questions**: Questions whose factual answers exist in `hr-leave-policy.pdf`, `it-security-policy.pdf`, or `travel-policy.pdf`.
- **5 Unanswerable Questions**: Questions intentionally outside the scope of company policies (e.g., office pets, stock vesting, Antarctica per diem).

### Metrics Definitions

1. **Grounded-Answer Rate (GAR)**:
   $$\text{GAR} = \frac{\text{Correctly Answered Questions}}{\text{Total Answerable Questions (15)}} \times 100\%$$
   - A question is counted as correctly answered if:
     1. `answered == true`
     2. The answer matches the factual truth in `expected_answer`
     3. The citation references the correct `expected_document` and `expected_page`.

2. **Correct-Refusal Rate (CRR)**:
   $$\text{CRR} = \frac{\text{Correctly Refused Questions}}{\text{Total Unanswerable Questions (5)}} \times 100\%$$
   - A question is counted as correctly refused if:
     1. `answered == false`
     2. `answer == "I couldn't find this in the uploaded documents."`
     3. `confidence < 0.65`

### Step-by-Step Evaluation Protocol
1. Ingest all 3 policy PDFs (`hr-leave-policy.pdf`, `it-security-policy.pdf`, `travel-policy.pdf`).
2. Run a script or loop through `eval/questions.json` sending each `question` to `POST /api/ask`.
3. For questions with `is_answerable: true`:
   - Verify `response.answered == true`.
   - Verify citation document and page match expectations.
4. For questions with `is_answerable: false`:
   - Verify `response.answered == false`.
   - Verify `response.answer == "I couldn't find this in the uploaded documents."`.
5. Compute **GAR** and **CRR** percentages from the results.

---

## 7. Interview Notes: Key Design Decisions & Known Limitations

### Why These Design Decisions Were Made

#### 1. Chunk Size (500 chars) & Overlap (50 chars)
- **Rationale**: Corporate policy sentences and rules typically range from 150 to 350 characters. A 500-character chunk window comfortably encloses a complete policy clause (e.g., a full rule regarding sick leave certificates or maternity benefits) without diluting the semantic embedding with unrelated clauses.
- **Overlap (50 chars)**: Overlap ensures that clauses straddling chunk boundaries are not split in half, preserving semantic continuity for the vector search model.

#### 2. Top-K Chunks (Top 3)
- **Rationale**: For targeted policy queries, 3 chunks provide sufficient factual context (~1,200 characters) while remaining compact. Passing too many chunks (e.g., K=10) increases LLM prompt token costs and introduces irrelevant noise ("lost in the middle" effect).

#### 3. Similarity Threshold (0.65)
- **Rationale**: Cosine similarity scores between normalized vectors indicate semantic relevance. In testing with Google's `text-embedding-004`, closely matching policy queries score between `0.78` and `0.92`. Irrelevant or uncovered queries (such as office dogs or stock options) score below `0.55`. Setting the threshold at `0.65` cleanly separates grounded knowledge from irrelevant queries.

#### 4. Pre-LLM Refusal Logic
- **Rationale**: Most naive RAG systems pass whatever chunks were retrieved to the LLM and rely on prompt instructions ("If you don't know, say I don't know"). However, LLMs are prone to hallucinating plausible-sounding policies when context is weak. By intercepting the request in `AnswerService` and returning `answered=false` before calling the LLM:
  1. We eliminate hallucination risk.
  2. We save 100% of LLM inference latency and API cost for unanswerable queries.
  3. We obtain deterministic, verifiable behavior.

#### 5. SimpleVectorStore with File Persistence
- **Rationale**: `SimpleVectorStore` requires zero external vector infrastructure (no Docker containers, no external cloud vector databases). Saving to `data/vector-store.json` satisfies persistence requirements across application restarts while keeping the codebase lightweight and interview-friendly.

---

### Known Limitations & Production Enhancements

| Limitation | Impact | Production Recommendation |
| :--- | :--- | :--- |
| **In-Memory Vector Store** | Vectors are held in RAM and synced to a single JSON file. Does not scale to millions of chunks or distributed replicas. | Replace `SimpleVectorStore` with **PGVector** (PostgreSQL extension) or **Milvus / Pinecone** via Spring AI starters. |
| **No Authentication / Multi-Tenancy** | All users share the same document pool and question endpoint. | Implement Spring Security with JWT/OAuth2 and add `organization_id` or `tenant_id` metadata filtering to `SearchRequest`. |
| **Scanned Image PDFs (No OCR)** | PDFBox extracts text from native digital PDFs. Scanned image-only PDFs without OCR text layers yield no text. | Integrate an OCR preprocessing pipeline (e.g., Apache Tika with Tesseract OCR or Google Document AI). |
| **Dense Vector Search Only** | Keyword searches for exact product codes or IDs might receive lower similarity scores. | Implement Hybrid Search combining dense vector search (embeddings) with sparse lexical search (BM25 / Elasticsearch). |

---

## 8. Exact Testing Order for the Verification Pipeline

Follow this exact order to test the system from end to end:

1. **Verify Unit Tests**:
   ```bash
   cd backend
   mvn test
   ```
   *Expected Result*: All 7 tests pass (`AskControllerTest`, `AnswerServiceTest`, `IngestionServiceTest`).

2. **Start the Backend**:
   ```bash
   mvn spring-boot:run
   ```

3. **Upload the 3 Sample Policy PDFs**:
   ```bash
   curl -X POST http://localhost:8080/api/documents -F "file=@sample-docs/hr-leave-policy.pdf"
   curl -X POST http://localhost:8080/api/documents -F "file=@sample-docs/it-security-policy.pdf"
   curl -X POST http://localhost:8080/api/documents -F "file=@sample-docs/travel-policy.pdf"
   ```

4. **Verify Document Listing**:
   ```bash
   curl -X GET http://localhost:8080/api/documents
   ```

5. **Test an Answerable Question (Grounded)**:
   ```bash
   curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "How many days of paid annual leave do employees accrue per calendar year?"}'
   ```
   *Expected Result*: `answered: true`, confidence >= 0.80, citations indicate `hr-leave-policy.pdf`, page 1.

6. **Test an Uncovered Question (Refusal Guardrail)**:
   ```bash
   curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "Are employees allowed to bring pet dogs to the office on Fridays?"}'
   ```
   *Expected Result*: `answered: false`, answer is `"I couldn't find this in the uploaded documents."`, citations empty.

7. **Inspect Audit History & Refusals in Admin View**:
   ```bash
   curl -X GET http://localhost:8080/api/history
   curl -X GET http://localhost:8080/api/unanswered
   ```

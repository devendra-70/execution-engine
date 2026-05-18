# Generic API Test Behaviour Guide

## Purpose
This file defines generic API testing behaviour rules that any test-document generator agent can use to create API-specific test cases from user stories, acceptance criteria, SRS documents, API contracts, endpoint descriptions, or functional requirements.

The agent must use this file only as behavioural guidance. It must not generate project-specific endpoints, payloads, users, tokens, or business rules unless those details are present in the input requirements.

---

## API Test Case Generation Principle
Generate API test cases only when a requirement involves backend request/response behaviour, endpoint processing, data validation, authentication, authorization, integration, or service-level output.

An API test case must validate what a client receives from the system: status code, response body, headers, error message, data state, or contract behaviour.

---

## When to Classify a Requirement as API
Classify a requirement as API if it mentions or implies any of the following:

- Endpoint, API, service, request, response, payload, resource, route
- HTTP methods: GET, POST, PUT, PATCH, DELETE
- Status codes: 200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500
- JSON/XML request or response body
- Authentication token, session token, API key, OAuth, JWT, bearer token
- Backend validation, schema validation, business rule validation
- CRUD operation at service level
- File upload/download API
- Search/filter/sort/pagination API
- Integration with third-party services
- Rate limit, timeout, retry, idempotency, concurrency, or error handling

---

## Do Not Generate API Test Cases For
Do not create API test cases for requirements that are only visual and have no service-level behaviour specified, such as:

- Pure UI layout or styling
- Button color, font size, spacing, alignment
- Visual-only content unless backed by data from an API
- Browser compatibility unless an API response is involved
- Internal algorithms, model prompts, or pipelines unless their output is returned through an API contract

---

## Generic API Behaviour Categories

### 1. Endpoint Availability and Method Validation
For every endpoint or backend action, generate cases for:
- Correct HTTP method succeeds
- Unsupported HTTP method returns proper error
- Endpoint path exists and is reachable
- Wrong or malformed endpoint returns 404 or suitable error

### 2. Successful Request Behaviour
Generate positive cases for valid requests.

Cover:
- Valid request with required headers and payload
- Correct success status code such as 200, 201, or 204
- Response body contains expected fields
- Response data types are correct
- Response headers are correct when specified
- Created/updated/deleted resource state is correct

### 3. Request Payload Validation
Generate negative cases for invalid payloads.

Cover:
- Missing required fields
- Null values
- Empty string values
- Invalid data type
- Invalid format such as email, date, phone, enum, UUID
- Min/max length boundaries
- Numeric boundaries such as min, max, negative, zero
- Extra unknown fields if schema strictness is required
- Malformed JSON/XML

### 4. Authentication Behaviour
If authentication is required, generate cases for:
- Valid token/API key/session succeeds
- Missing token returns 401
- Invalid token returns 401
- Expired token returns 401
- Tampered token returns 401
- Token with wrong audience/scope/issuer if specified

### 5. Authorization Behaviour
If roles or permissions are mentioned, generate cases for:
- Authorized role can access endpoint
- Unauthorized role gets 403
- User cannot access another user’s private resource
- Admin-only operation is blocked for non-admin user
- Direct object reference access is denied when not permitted

### 6. CRUD Resource Behaviour
For resource-based features, generate cases for:
- Create resource
- Retrieve resource by ID
- Retrieve resource list
- Update full resource
- Partially update resource if PATCH is supported
- Delete resource
- Retrieve deleted/non-existing resource
- Duplicate create conflict if uniqueness is required

### 7. Search, Filter, Sort, and Pagination Behaviour
If listing APIs are present, generate cases for:
- Default list response
- Filter by valid field
- Filter by invalid field/value
- Search with matching data
- Search with no matching data
- Sort ascending/descending
- Pagination first page, middle page, last page
- Page size boundary
- Invalid page number/page size

### 8. File Upload and Download API Behaviour
If file APIs exist, generate cases for:
- Valid file upload
- Unsupported file type
- File size limit exceeded
- Empty file upload
- Missing multipart field
- Corrupted file
- Successful file download
- Download of unauthorized or missing file
- Correct content type and file name header

### 9. Error Handling Behaviour
Generate error cases for:
- Bad request returns structured validation error
- Unauthorized returns consistent error body
- Forbidden returns clear permission error
- Not found returns resource-not-found error
- Conflict returns duplicate/state conflict error
- Unsupported media type returns 415 when applicable
- Server error returns safe message without exposing stack trace

### 10. Idempotency, Duplicate, and Concurrency Behaviour
When relevant, generate cases for:
- Repeating same POST with idempotency key does not duplicate resource
- Double-submit request handling
- Concurrent update conflict
- Version mismatch or stale update if supported
- Retry after timeout does not corrupt data

### 11. Performance and Reliability Behaviour
When non-functional API requirements exist, generate cases for:
- Response time threshold
- Load with expected concurrent users
- Rate limit threshold and 429 response
- Timeout behaviour
- Retry behaviour only when visible through API response/status

### 12. Security Behaviour
For APIs, include security-oriented cases when relevant:
- SQL injection payload rejected/sanitized
- XSS payload not stored unsafely
- Sensitive data not returned in response
- Password/token/secret not logged or returned
- CORS behaviour if specified
- HTTPS requirement if specified

---

## API Test Case Structure
Every generated API test case should include:

- test_type: API
- tc_id
- story_ref
- feature
- category
- priority
- http_method
- endpoint
- request_headers
- request_payload
- expected_status_code
- expected_response
- preconditions
- test_data
- postconditions

If the project provides a fixed API test-case template, map these fields to the exact column names of that template.

---

## Generic API Test Design Techniques
Use only techniques relevant to the requirement.

- UC: Main API workflow and alternate API flow
- EP: Valid and invalid request partitions
- BVA: Boundary values for payload fields and pagination parameters
- DT: Multi-condition rules such as role + resource state + request action
- ST: API-visible resource lifecycle transitions
- EG: Missing token, malformed payload, invalid ID, duplicate request, timeout
- PW: Parameter combinations such as role + status + payload type + method

---

## API Test Case ID Format
Recommended format:

`TC-API-{FEATURE_CODE}-{TECHNIQUE}-{NNN}`

Examples:
- TC-API-LOGIN-UC-001
- TC-API-USERCRUD-EP-002
- TC-API-SEARCH-BVA-003

Feature code must be derived from the actual user story, API name, endpoint name, or requirement title.

---

## Common Expected Status Code Guidance
Use actual project/API contract status codes when available. If not available, use common REST conventions:

- 200 OK: Successful read/update/action response
- 201 Created: Successful resource creation
- 204 No Content: Successful delete or action with no body
- 400 Bad Request: Malformed or invalid request
- 401 Unauthorized: Missing/invalid authentication
- 403 Forbidden: Authenticated but not allowed
- 404 Not Found: Resource or route not found
- 409 Conflict: Duplicate or conflicting state
- 415 Unsupported Media Type: Invalid content type
- 422 Unprocessable Entity: Semantic validation failure if used by project
- 429 Too Many Requests: Rate limit exceeded
- 500 Internal Server Error: Unexpected server failure; should return safe error body

---

## Traceability Rule
Every API test case must reference the exact user story ID, functional requirement ID, acceptance criterion ID, or endpoint requirement ID.

Do not generate any API test case that cannot be traced to a requirement.

---

## Output Quality Rules
The generated API test cases must be:

- Based only on the input requirement/API contract
- Clear about method, endpoint, headers, payload, expected status, and expected response
- Executable manually in Postman or automatable using RestAssured, SuperTest, Pytest, Karate, or similar tools
- Focused on externally observable API behaviour
- Free from assumptions about internal implementation unless stated in the requirement

# Generic UI Test Behaviour Guide

## Purpose
This file defines generic UI testing behaviour rules that any test-document generator agent can use to create UI-specific test cases from user stories, acceptance criteria, SRS documents, or functional requirements.

The agent must use this file only as behavioural guidance. It must not generate project-specific test cases unless those details are present in the input requirements.

---

## UI Test Case Generation Principle
Generate UI test cases only when a requirement involves something visible or interactive in the browser, mobile screen, desktop UI, or any user-facing interface.

A UI test case must validate what the user can see, click, type, select, upload, download, navigate to, or receive as feedback.

---

## When to Classify a Requirement as UI
Classify a requirement as UI if it mentions or implies any of the following:

- Page, screen, dashboard, panel, modal, popup, dialog, toast, tab, menu, sidebar, header, footer
- Form, input field, dropdown, checkbox, radio button, date picker, file upload, search box
- Button actions such as Submit, Save, Cancel, Delete, Add, Update, Search, Filter, Export, Login, Logout
- Visual messages such as success message, error message, validation message, warning, loader, empty state
- Navigation such as redirect, page transition, breadcrumb, route access, back/forward behaviour
- Data display such as table, grid, card, list, chart, report, profile details, order details
- User role behaviour shown in UI, such as admin-only buttons or restricted pages
- Browser/device compatibility, responsiveness, accessibility, or usability

---

## Do Not Generate UI Test Cases For
Do not create UI test cases for requirements that are purely backend/internal and have no visible user-facing behaviour, such as:

- Internal service-to-service communication
- Database-only operations without visible UI result
- Internal algorithms, queues, schedulers, jobs, or pipelines
- Internal AI/model logic unless the result is displayed to a user
- Logs, metrics, or monitoring unless the user story describes a visible dashboard/report

---

## Generic UI Behaviour Categories

### 1. Page Load and Navigation
Validate that the user can open the required page/screen successfully.

Generate test cases for:
- Page opens from correct menu/link/URL
- Correct page title, heading, or main content is displayed
- Unauthorized users are redirected or blocked
- Back/forward browser behaviour does not break the flow
- Deep links open the correct page when allowed
- Invalid routes display a proper not-found or access-denied page

### 2. Form Field Validation
For every input form, generate positive and negative UI validation test cases.

Cover:
- Mandatory fields left blank
- Valid input accepted
- Invalid format rejected
- Minimum and maximum length boundaries
- Special characters where relevant
- Leading/trailing spaces
- Duplicate values if the story mentions uniqueness
- Disabled, read-only, or hidden fields
- Error message visibility and clarity

### 3. Button and Action Behaviour
Validate all visible user actions.

Cover:
- Button is visible and enabled only when conditions are met
- Button click triggers expected UI response
- Double-click or repeated click does not create duplicate action
- Cancel/close action returns user safely
- Save/submit action shows confirmation or success message
- Delete/destructive action asks confirmation when applicable

### 4. Data Display Behaviour
For tables, cards, lists, dashboards, or reports, validate displayed data.

Cover:
- Data appears in expected columns/fields
- Empty state is shown when no data exists
- Loading state is shown while data is being fetched
- Error state is shown if data cannot be loaded
- Pagination works correctly
- Sorting works correctly
- Filtering works correctly
- Search works correctly
- Data refresh displays latest available result

### 5. Role-Based UI Behaviour
If roles or permissions are mentioned, generate UI access test cases.

Cover:
- Authorized user can see and access allowed UI elements
- Unauthorized user cannot see restricted buttons/menus/pages
- Direct URL access to restricted page is blocked
- Role-specific dashboard/menu/options are shown correctly

### 6. File Upload and Download UI Behaviour
If upload/download is present, generate test cases for:
- Valid file upload
- Unsupported file type
- File size boundary
- Empty file
- Duplicate file upload
- Upload progress/loader
- Upload success/error message
- Download button visibility
- Downloaded file name/type/content basic validation

### 7. Responsive and Cross-Browser Behaviour
For user-facing screens, include compatibility cases when relevant.

Cover:
- Layout works on desktop, tablet, and mobile viewport
- No overlapping or clipped content
- Main actions remain accessible
- Form fields remain usable
- Tested browsers may include Chrome, Edge, Firefox, and Safari if required by project context

### 8. Accessibility and Usability Behaviour
Generate basic accessibility/usability cases when UI is involved.

Cover:
- Labels are associated with input fields
- Keyboard navigation works for main actions
- Focus indicator is visible
- Error messages are readable and placed near fields
- Required fields are clearly marked
- Color is not the only indicator of status/error

### 9. Session and Authentication UI Behaviour
If login/session/logout is mentioned, generate UI cases for:
- Successful login
- Invalid login message
- Logout redirects user properly
- Session timeout redirects user to login
- User cannot access protected pages after logout
- Remember-me or forgot-password flows only if mentioned

### 10. Error and Edge UI Behaviour
Generate UI error/edge cases for:
- Network/API failure shown as user-friendly message
- Slow response shows loader/spinner
- Empty API response shows empty state
- Unexpected validation failure shows clear error
- User refreshes page during action
- User navigates away with unsaved changes if applicable

---

## UI Test Case Structure
Every generated UI test case should include:

- test_type: UI
- tc_id
- story_ref
- feature
- technique
- category
- priority
- preconditions
- test_steps
- test_data
- expected_result
- postconditions

---

## Generic UI Test Design Techniques
Use only techniques relevant to the requirement.

- UC: Happy path and alternate user journeys
- EP: Valid and invalid input partitions
- BVA: Minimum, maximum, below-minimum, above-maximum field boundaries
- DT: Multi-condition UI behaviour such as role + status + action availability
- ST: Page/entity status transitions visible in UI
- EG: Common UI failures such as blank input, invalid format, double submit, session timeout
- PW: Browser + role + device + data combinations where useful

---

## UI Test Case ID Format
Recommended format:

`TC-UI-{FEATURE_CODE}-{TECHNIQUE}-{NNN}`

Examples:
- TC-UI-LOGIN-UC-001
- TC-UI-USERFORM-BVA-002
- TC-UI-DASHBOARD-EG-003

Feature code must be derived from the actual user story or requirement title.

---

## Traceability Rule
Every UI test case must reference the exact user story ID, functional requirement ID, or acceptance criterion ID.

Do not generate any UI test case that cannot be traced to a requirement.

---

## Output Quality Rules
The generated UI test cases must be:

- Specific to the input requirement
- Written from the end-user perspective
- Executable manually or automatable using Selenium, Playwright, Cypress, or similar tools
- Free from internal implementation details unless they are visible to the user
- Clear enough for QA, developers, and product owners to understand

# Java 21 Code Quality Instructions

This document defines the mandatory Java 21 quality gates used for all code reviews on this project.
Any agent or reviewer must apply every gate below when evaluating Java source code.

---

## Gate 1 — Java 21 Language Feature Adoption

Enforce that code written on a Java 21 codebase uses modern constructs and does not regress to pre-Java-16 idioms.
Each sub-gate is marked BLOCKING or SUGGESTION.

### 1.1 Records (BLOCKING)
Data-carrying classes with no business logic (DTOs, value objects, response/request payloads) must use `record` instead of a traditional class with fields, constructor, getters, `equals`, `hashCode`, and `toString`.
Violation signal: a class whose only non-`static` methods are accessors and whose fields are all `final`.

```java
// ✅ Correct
public record UserDto(String name, String email) {}

// ❌ Wrong
public class UserDto {
    private final String name;
    public String getName() { return name; }
}
```

### 1.2 Sealed Classes and Interfaces (SUGGESTION → BLOCKING if domain model is closed)
Closed domain hierarchies (e.g. `Result`, `Shape`, `Event`, `Command`) must use `sealed` + `permits` to restrict subclassing.
Permitted subtypes must be `final`, `sealed`, or `non-sealed`.

```java
// ✅ Correct
public sealed interface Shape permits Circle, Rectangle, Triangle {}

// ❌ Wrong
public abstract class Shape {} // open to any subclass
```

### 1.3 Pattern Matching — `instanceof` (BLOCKING)
All `instanceof` checks must use pattern matching. No manual cast on the line following an `instanceof`.

```java
// ✅ Correct
if (obj instanceof String s) { doSomething(s); }

// ❌ Wrong
if (obj instanceof String) { String s = (String) obj; }
```

### 1.4 Pattern Matching — `switch` Expressions (BLOCKING)
`switch` over types must use pattern matching with `case Type t ->` syntax.
Exhaustive `switch` over sealed hierarchies must not include an unreachable `default` branch.
`when` guards must replace nested `if` inside a `case` block.

```java
// ✅ Correct
return switch (shape) {
    case Circle c    -> Math.PI * c.radius() * c.radius();
    case Rectangle r -> r.width() * r.height();
};

// ❌ Wrong
if (shape instanceof Circle) { … }
else if (shape instanceof Rectangle) { … }
```

### 1.5 Record Patterns / Deconstruction (SUGGESTION)
When destructuring a `record` inside `instanceof` or `switch`, use record patterns instead of separate accessor calls.

```java
// ✅ Correct
if (obj instanceof Point(int x, int y)) { … }

// ❌ Less idiomatic
if (obj instanceof Point p) { int x = p.x(); int y = p.y(); }
```

### 1.6 Virtual Threads (SUGGESTION → BLOCKING for new thread management code)
New code that creates or manages threads for I/O-bound work must not use `new Thread(…)` or `Executors.newFixedThreadPool(N)` for I/O tasks.
Use `Executors.newVirtualThreadPerTaskExecutor()` or `Thread.startVirtualThread(task)`.

```java
// ✅ Correct
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    exec.submit(task);
}

// ❌ Wrong — blocks OS threads for I/O work
ExecutorService pool = Executors.newFixedThreadPool(200);
```

### 1.7 Structured Concurrency (SUGGESTION)
Methods that fork multiple subtasks and join them should use `StructuredTaskScope` instead of `CompletableFuture.allOf` or manual `Future.get()` chains.

```java
// ✅ Correct
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var user  = scope.fork(this::fetchUser);
    var order = scope.fork(this::fetchOrder);
    scope.join().throwIfFailed();
    return new Response(user.resultNow(), order.resultNow());
}

// ❌ Wrong
CompletableFuture.allOf(userFuture, orderFuture).join();
```

### 1.8 Scoped Values (SUGGESTION)
New code passing contextual/request-scoped data down a call stack (trace IDs, tenant IDs, user context) must not use `ThreadLocal`.
Use `ScopedValue` instead, especially when combined with virtual threads.

```java
// ✅ Correct
ScopedValue.where(CURRENT_USER, user).run(this::process);

// ❌ Wrong
ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();
```

### 1.9 Sequenced Collections (SUGGESTION)
Code accessing the first or last element of a `List` must use `getFirst()` / `getLast()` instead of `get(0)` / `get(list.size() - 1)`.
Code iterating in reverse must use `.reversed()` instead of a manual index loop.

```java
// ✅ Correct
list.getFirst();
list.getLast();
list.reversed().forEach(…);

// ❌ Wrong
list.get(0);
list.get(list.size() - 1);
```

### 1.10 Type Inference with `var` (SUGGESTION)
`var` must be used for local variables where the type is immediately obvious from the right-hand side.
`var` must not be used when the inferred type would be unclear to a reader.

```java
// ✅ Correct
var users = new ArrayList<User>();

// ❌ Wrong — type is not obvious
var result = service.compute();
```

---

## Gate 2 — Immutability and Functional Style (BLOCKING)

### 2.1 Immutable Collections
Fixed/constant collections must use `List.of()`, `Set.of()`, `Map.of()`.
Stream terminal operations producing a list that is never mutated must use `Stream.toList()`, not `Collectors.toList()`.

### 2.2 Optional for Absent Values
Methods must not return `null` to indicate absence — return `Optional<T>` instead.
`Optional` must only be used as a return type, never as a field, constructor parameter, or method parameter.
Null checks on return values must be replaced with `Optional` chaining (`map`, `flatMap`, `orElseThrow`).

### 2.3 Final Fields
All fields never reassigned after construction must be declared `final`.
Classes with only `static` utility methods must be `final` with a `private` constructor.

### 2.4 Streams and Method References
Collection iteration with side-effect-free transforms must use the Streams API.
Simple lambdas that delegate to a single method must use method references (`Foo::bar`).

```java
// ✅ Correct
users.stream().map(User::getName).toList();

// ❌ Wrong
for (User u : users) { names.add(u.getName()); }
```

---

## Gate 3 — Naming Conventions (Google Java Style) (BLOCKING)

| Identifier | Convention | Example |
|---|---|---|
| Class / Interface / Record / Enum | `UpperCamelCase` — nouns only | `OrderService`, `UserRecord` |
| Method | `lowerCamelCase` — verbs | `getUserById`, `calculateTotal` |
| Variable / Parameter | `lowerCamelCase` | `userName`, `orderCount` |
| Constant (`static final`) | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| Package | `lowercase`, no underscores | `com.example.order` |

Additional rules:
- No abbreviations unless universally known (`id`, `url`, `dto` acceptable; `usr`, `mgr`, `proc` not acceptable).
- No Hungarian notation (`strName`, `intCount`).
- Boolean variables/methods must use `is`, `has`, `can`, `should` prefix (`isActive`, `hasPermission`).
- Test method names must follow `methodName_scenario_expectedBehaviour` or `should_expectedBehaviour_when_scenario`.

---

## Gate 4 — Bug Patterns (all BLOCKING)

| Violation | Example to Flag |
|---|---|
| Resource not closed — use try-with-resources for `InputStream`, `Connection`, `PreparedStatement`, `HttpClient`, files, sockets | `InputStream is = new FileInputStream(f); // never closed` |
| Object identity comparison (`==`) used where `.equals()` is required | `if (status == "ACTIVE")` |
| Redundant or unsafe cast | `(String)(Object) value` |
| Condition always `true` or always `false` | `if (list != null && list != null)` |
| Unreachable code after unconditional `return` or `throw` | Lines after `return result;` inside the same block |
| Null dereference — method called on a value that may be `null` without a null guard | `user.getName()` where `user` may be `null` |
| Merging unrelated exception types in a single `catch` | `catch (IOException \| NullPointerException e)` |
| Identical expressions on both sides of a binary operator | `if (a == a)` |
| `@NonNull` parameter annotated but `null` passed at the call site | Any call site passing `null` into a `@NonNull` parameter |

---

## Gate 5 — Code Smells

| Severity | Violation |
|---|---|
| BLOCKING   | More than 5 parameters on a method — use a builder, config record, or helper class |
| BLOCKING   | Duplicated code blocks of 5+ lines — extract to a shared `private` method |
| SUGGESTION | Method longer than 40 lines — extract smaller, testable units |
| BLOCKING   | Cognitive complexity > 15 — simplify nested `if`/`for`/`switch` chains |
| SUGGESTION | String literal used 3+ times — replace with a named constant or enum |
| SUGGESTION | Unused variable assignment — remove dead variables |
| SUGGESTION | Magic numbers — replace with a named `static final` constant |
| BLOCKING   | Empty `catch` block — always log or rethrow; never swallow exceptions silently |
| SUGGESTION | Redundant `throws` declaration — remove checked exception if never thrown |
| BLOCKING   | Class name shadows a parent class or interface name |
| SUGGESTION | `private` method that does not use instance state should be `static` |

---

## Gate 6 — Concurrency Safety (BLOCKING)

- Shared mutable state accessed from multiple threads without `synchronized`, `volatile`, or a concurrent collection is BLOCKING.
- `HashMap` used where concurrent access is possible → must be `ConcurrentHashMap`.
- `SimpleDateFormat` (not thread-safe) must not be a shared field — use `DateTimeFormatter` instead.
- `ThreadLocal` for request-scoped context in a virtual-thread-aware codebase → SUGGESTION to migrate to `ScopedValue`.
- Fixed-size thread pools for I/O-bound tasks in new code → BLOCKING (see Gate 1.6).

---

## Gate 7 — Security (all BLOCKING)

- **Hardcoded credentials**: any `String` whose name contains `password`, `secret`, `token`, `key`, `apiKey`, or `credential`, or whose value matches a common secret pattern (e.g. `Bearer `, `glpat-`, `AKIA`) must be flagged.
- **SQL injection**: string concatenation inside a JDBC `executeQuery` or `executeUpdate` — must use `PreparedStatement` with `?` placeholders.
- **Console output in production code**: `System.out` / `System.err` in non-test code must be replaced with SLF4J (`log.info(…)`) or Logback.
- **Insecure random**: `java.util.Random` used for security purposes (session IDs, tokens, OTPs) must be replaced with `SecureRandom`.
- **Deserialization**: `ObjectInputStream` without class filtering is a security risk.

---

## Gate 8 — Testing

| Severity | Rule |
|---|---|
| BLOCKING   | Any new `public` method containing business logic with no corresponding test |
| SUGGESTION | New branches (`if`, `switch`, `try/catch`) without test coverage for each path |
| BLOCKING   | Tests using JUnit 4 (`org.junit.Test`) — must migrate to JUnit 5 (`org.junit.jupiter.api.Test`) |
| SUGGESTION | Bare `assertEquals` for complex objects — prefer AssertJ (`assertThat(…).isEqualTo(…)`) |
| SUGGESTION | `@SpringBootTest` used for a pure unit test — prefer `@ExtendWith(MockitoExtension.class)` |

---

## Gate 9 — Javadoc and Comments (all SUGGESTION)

- Public API methods (`public` on a non-test, non-`record` class) should have Javadoc with `@param`, `@return`, and `@throws` where applicable.
- Inline comments must explain **why**, not **what** — remove comments that merely restate what the code does.
- `TODO` / `FIXME` comments must reference a ticket number (e.g. `// TODO PROJ-123: …`). Untracked TODOs are flagged.
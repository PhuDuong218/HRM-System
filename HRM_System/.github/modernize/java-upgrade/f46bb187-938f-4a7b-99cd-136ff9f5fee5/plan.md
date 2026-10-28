# Upgrade Plan: hrm (f46bb187-938f-4a7b-99cd-136ff9f5fee5)

- **Generated**: 2026-10-21 07:19:03 +07:00
- **HEAD Branch**: Viet
- **HEAD Commit ID**: unavailable (version-control integration was unavailable)

## Available Tools

**JDKs**
- JDK 25.0.4.1: `C:\Users\Viet\.jdk\jdk-25.0.4.1+1` (target runtime)

**Build Tools**
- Maven Wrapper 3.9.16: `mvnw.cmd`

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

- Run in auto-execution mode.
- Preserve unrelated pre-existing worktree changes.

## Options

- Working branch: N/A (version control integration unavailable)
- Run tests before and after the upgrade: true

## Upgrade Goals

- Java runtime and compilation target 25 (latest LTS)

## Technology Stack

| Technology/Dependency | Current | Min Compatible Version | Why Incompatible |
| --------------------- | ------- | ---------------------- | ---------------- |
| Java | 21 | 25 | User requested |
| Spring Boot | 4.1.1 | 4.1.1 | Already compatible with Java 25 |
| Maven Wrapper | 3.9.16 | 3.9+ | Already compatible |
| Maven Compiler configuration | Spring Boot managed | 25-capable compiler | Managed configuration uses the selected JDK |

## Derived Upgrades

- Set the Maven `java.version` property to 25 so Spring Boot dependency management and compiler configuration target Java 25.
- No wrapper change is required because Maven Wrapper 3.9.16 is already present and compatible.
- No Kotlin upgrade is required because the project contains no Kotlin sources or Kotlin build configuration.

## Impact Analysis

### Dependency Changes

| File | Dependency | Current | Action | Target | Reason |
|------|------------|---------|--------|--------|--------|
| `pom.xml` | `java.version` | 21 | upgrade | 25 | Set the requested Java runtime/compiler target |

### Source Code Changes

No source changes are required. The source uses standard Java and Spring APIs with no target-incompatible patterns found.

### Configuration Changes

No application configuration changes are required.

### CI/CD Changes

No CI/CD files or hardcoded Java runtime references were found in the Maven project.

### Risks & Warnings

- Existing uncommitted edits in `pom.xml` and `src/main/resources/application.properties` predate this upgrade and must be preserved.
- Runtime integration tests may require a local SQL Server instance; the context test result will determine whether that external dependency is active.

## Upgrade Steps

- Step 1: Update Maven Java target
  - **Rationale**: Change the project compilation/runtime target while preserving the existing Spring Boot and Maven versions.
  - **Changes to Make**: Apply the Dependency Changes entry for `pom.xml`.
  - **Verification**: `mvnw.cmd clean test-compile`; JDK 25; compilation succeeds.

- Step 2: Final validation
  - **Rationale**: Confirm production and test compilation and execute the complete test suite on Java 25.
  - **Changes to Make**: None beyond Step 1.
  - **Verification**: `mvnw.cmd clean test`; JDK 25; all tests pass.

# Java Runtime Upgrade Summary

## Result

- Upgraded the Maven Java target from 21 to 25, the latest LTS runtime.
- Java 25.0.4.1 and Maven Wrapper 3.9.16 were already installed.
- Spring Boot 4.1.1 and all existing source code compiled successfully without source rewrites.

## Validation

- `mvnw.cmd clean test-compile`: passed; 22 production files and 1 test file compiled with release 25.
- `mvnw.cmd clean test`: passed; 1/1 tests passed.
- Direct dependency review completed for Spring Boot starters and Microsoft SQL Server JDBC.

## Files Changed

- `pom.xml`: changed `java.version` from `21` to `25`.
- `.github/modernize/java-upgrade/f46bb187-938f-4a7b-99cd-136ff9f5fee5/`: added plan, progress, and summary records.

## Risks and Notes

- Existing unrelated edits in `pom.xml` and `src/main/resources/application.properties` were preserved.
- Mockito reports that dynamic Java-agent loading will be disallowed by default in a future JDK release. This is a warning from the current test dependency configuration and did not affect Java 25 validation.
- Version-control integration was unavailable, so no commit was created.

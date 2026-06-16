# Runway

Runway is a native-first, reflection-free database migration engine for Java applications.

SQL files are build inputs for `runway-codegen`. They are not loaded as runtime resources.

```java
MigrationResult result = Runway.migrate(
    dataSource,
    PostgreSqlDialect.INSTANCE,
    GeneratedRunwayMigrations.registry()
);
```

Dialects are explicit Java objects. Runway does not inspect drivers, scan the classpath or discover implementations dynamically.
That same model supports multiple dialects for one database family: add another `DatabaseDialect` implementation and pass it directly,
for example a future `MySql8Dialect.INSTANCE`.

## Modules

- `runway-core`: API, planner, JDBC runtime, PostgreSQL, MySQL, MariaDB and SQLite dialects.
- `runway-codegen`: command-line SQL-to-Java registry generator.
- `runway-integration-tests`: consumer-style SQLite integration tests.

## Generate Migrations

```bash
java -cp runway-codegen/target/runway-codegen-0.1.0-SNAPSHOT.jar \
  io.github.absketches.runway.codegen.RunwayCodegen \
  --input src/main/runway \
  --output build/generated/sources/runway/main/java \
  --package io.github.absketches.runway.generated \
  --class-name GeneratedRunwayMigrations
```

Supported file names:

- `V1__create_users.sql`
- `R__refresh_view.sql`

## Build

```bash
mvn verify
```

## Releases

GitHub Actions runs `build_test` for pull requests and pushes to every branch.
Releases are manual and can only run from `main` after `build_test` has passed for the source commit being released.

Published artifacts:

- `io.github.absketches:runway-core`
- `io.github.absketches:runway-codegen`

`runway-integration-tests` is marked with `maven.deploy.skip=true` and is never published.

The parent POM is published with each release as supporting Maven metadata. `runway-codegen` has no main-scope dependency on `runway-core`,
so codegen-only changes can be released without publishing a new core artifact.

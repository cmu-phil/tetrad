# Tetrad Project Guidelines

This document provides guidelines for working on the Tetrad project with Junie.

## Project Structure
The project is a multi-module Maven project.
- **`tetrad-lib`**: Contains the core causal inference algorithms and models. This is where most logic resides.
- **`tetrad-gui`**: Contains the Java Swing-based graphical user interface (GUI) and view code.
- **`data-reader`**: A module for data ingestion.

## Build and Tests
Standard Maven commands are used for building and testing.
- **Compile**: `mvn clean compile`
- **Run all tests**: `mvn clean test`
- **Run specific test**: `mvn -pl <module-name> test -Dtest=<TestClassName>`
- **Build package**: `mvn clean package` (The GUI launcher jar will be in `tetrad-gui/target`).

Java Version: **JDK 21** or later (based on `pom.xml`).

## Code Style
Please follow the existing patterns in the codebase:
- **Indentation**: 4 spaces.
- **Naming**: Standard Java `camelCase` for methods and variables, `PascalCase` for classes.
- **Logging**: Use `edu.cmu.tetrad.util.TetradLogger` for logging within `tetrad-lib`. Avoid `System.out.println`.
- **Comments & Javadoc**: Classes and public methods should have descriptive Javadocs. Include `@author` and `@see` where applicable.
- **Copyright Header**: Every source file must start with the standard GPL copyright header (see existing files for the current year and authors).

## Testing Guidelines
- **Framework**: Uses **JUnit 4**.
- **Location**: Test files are typically located in `src/test/java`.
- **Package**: In `tetrad-lib`, tests are often placed in the `edu.cmu.tetrad.test` package, regardless of the package of the class under test. Check for existing tests in that package before creating new ones.
- **Verification**: Always run relevant tests before submitting changes. For logic changes in `tetrad-lib`, ensure no regressions in `edu.cmu.tetrad.test`.

## Submitting Changes
- **No Refactoring**: Avoid broad refactorings unless explicitly requested.
- **Minimal Changes**: Keep changes focused on the issue description.
- **Documentation**: Update Javadoc if you change public APIs.

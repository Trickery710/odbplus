# Copilot Instructions for AI Coding Agents

## Project Overview
- **Type:** Multi-module Android/Kotlin project using Gradle Kotlin DSL
- **Modules:**
  - `app/`: Main Android application
  - `core-protocol/`, `core-transport/`: Core libraries for protocol and transport logic
  - `data-schema/`: Data models and schemas
  - `feature-*`: Feature modules (diagnostics, ecu-profile, live, logger)

## Architecture & Patterns
- **Modularization:** Each feature or core concern is a separate Gradle module. Shared logic lives in `core-*` modules; features are isolated in `feature-*` modules.
- **Data Flow:** Data models are defined in `data-schema/` and shared across modules. Communication between modules is via explicit dependencies in `build.gradle.kts`.
- **Dependency Injection:** If present, Hilt is used (see `app/build/generated/hilt/`).
- **Build System:** Uses Gradle Kotlin DSL (`build.gradle.kts`).

## Developer Workflows
- **Build All:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Build Specific Module:**
  ```bash
  ./gradlew :feature-logger:assembleDebug
  ```
- **Clean Build:**
  ```bash
  ./gradlew clean
  ```
- **Test (if tests exist):**
  ```bash
  ./gradlew test
  ```
- **Debug APKs:**
  - Find APKs in `app/build/outputs/apk/` after build.

## Conventions & Patterns
- **Gradle:** All module dependencies and settings are managed in `build.gradle.kts` and `settings.gradle.kts`.
- **Source Layout:**
  - Main code: `src/main/`
  - Generated code: `build/generated/`
- **ProGuard:** Each module may have its own `proguard-rules.pro`.
- **Version Catalog:** Shared dependencies are managed in `gradle/libs.versions.toml`.
- **Local Properties:** Use `local.properties` for local machine-specific settings (not checked in).

## Integration Points
- **External Libraries:** Managed via Gradle and version catalog.
- **Generated Code:** Hilt and other annotation processors output to `build/generated/`.
- **Cross-Module:** Use explicit Gradle dependencies; avoid direct source imports across modules.

## Examples
- To add a new feature module:
  1. Create `feature-new/` with standard structure.
  2. Register in `settings.gradle.kts`.
  3. Add dependencies in `feature-new/build.gradle.kts`.

---

**Edit this file to update project-specific AI agent instructions.**
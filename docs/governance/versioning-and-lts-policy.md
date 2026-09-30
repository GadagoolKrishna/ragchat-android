# Versioning, Deprecation & LTS Branch Strategy

Engineering guidelines governing SDK release lifecycles, semantic versioning, binary compatibility, and deprecation policies.

---

## 1. Semantic Versioning (SemVer 2.0.0)

RagChat Android SDK strictly follows [Semantic Versioning 2.0.0](https://semver.org/):

$$\text{MAJOR}.\text{MINOR}.\text{PATCH}$$

- **MAJOR (Breaking Changes)**:
  - Incompatible SPI interface modifications or removals in `:sdk-api`.
  - Non-backward-compatible database schema migrations requiring manual host intervention.
  - Removal of previously deprecated public classes or functions.
  - Minimum Android SDK target increase (e.g. bumping `minSdk` from 26 to 28).
- **MINOR (Backward-Compatible Features)**:
  - New SPI capabilities, model engine adapters, or UI composables.
  - Enhancements to chunkers, retrieval metrics, or governance policy rules.
  - Deprecation of existing public APIs with replacement alternatives.
- **PATCH (Backward-Compatible Bug Fixes)**:
  - Security vulnerability patches, regression fixes, memory optimizations, and documentation updates.

---

## 2. API Deprecation Policy

1. **Two-Minor-Version Window**: Any public API marked for deprecation must be annotated with `@Deprecated(message = "...", replaceWith = ReplaceWith(...), level = DeprecationLevel.WARNING)` for a minimum of two minor releases (e.g., deprecated in `v1.1`, remains warning in `v1.2`) before being elevated to `DeprecationLevel.ERROR` or removed in the next major version (`v2.0`).
2. **Binary Compatibility Validator (BCV)**: Every build enforces BCV checks (`apiCheck`). Any unintended binary change causes immediate CI build failure.

---

## 3. Branching & LTS Release Strategy

```
main (v1.1.0-dev)
  │
  ├──► release/v1.0 (LTS Branch: v1.0.0, v1.0.1, v1.0.2 ...)
  │      └── Backports: Critical security patches, bug fixes, MASVS updates
  │
  └──► Feature branches (feat/..., fix/...)
```

- **LTS Support Window**: Each LTS branch receives security and critical bug fixes for **18 months** following initial GA release.
- **Hotfix Delivery**: Hotfixes are tagged directly from release branches (`v1.0.x`) and published to Maven Central staging with priority verification.

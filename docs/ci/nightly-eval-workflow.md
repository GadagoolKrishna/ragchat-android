# Nightly Evaluation GitHub Actions Workflow

To enable the nightly evaluation in GitHub Actions, add the following file to `.github/workflows/nightly-eval.yml` in your repository:

```yaml
name: Nightly Evaluation & Quality Gates

on:
  schedule:
    - cron: '0 2 * * *' # Daily at 02:00 UTC
  workflow_dispatch:

jobs:
  eval-and-quality-gates:
    name: Run Eval & Quality Thresholds
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v3

      - name: Run Architecture & Quality Checks
        run: |
          ./gradlew checkNoAndroidImports
          ./gradlew spotlessCheck
          ./gradlew detekt

      - name: Run Unit Tests & Security Suite
        run: |
          ./gradlew :eval:test
          ./gradlew :sdk-android-storage:testDebugUnitTest

      - name: Run Golden Dataset Evaluation & Quality Thresholds
        run: |
          ./gradlew :eval:test --tests "com.ragchat.eval.GoldenDatasetTest"

      - name: Publish Evaluation Artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: eval-results
          path: |
            eval/build/reports/tests/test/
            build/reports/
```

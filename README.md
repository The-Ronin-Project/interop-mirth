# interop-mirth

## OWASP Dependency Check

The OWASP Dependency Check tool can be run with the following command:

```shell
./gradlew clean :mirth-channel-code:dependencyCheckAnalyze
```

This will generate a HTML report at `mirth-channel-code/build/reports/dependency-check-report.html`.

Any new suppressions should be added [here](mirth-channel-code/conf/owasp-suppress.xml).

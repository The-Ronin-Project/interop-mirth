# interop-mirth

Multi-project build containing InterOps data pipeline code featuring the Mirth integration engine by NextGen.

### Components

* [mirth-channel-code](mirth-channel-code) - Kotlin code: we build this to create a Java .jar file that we load into Mirth and invoke from our Mirth channels
* [mirth-channel-config](mirth-channel-config) - Mirth artifacts: channel exports as XML, Mirth code templates that call into our .jar file, and environment setup for running Mirth

## OWASP Dependency Check

The OWASP Dependency Check tool can be run with the following command:

```shell
./gradlew clean :mirth-channel-code:dependencyCheckAnalyze
```

This will generate a HTML report at `mirth-channel-code/build/reports/dependency-check-report.html`.

Any new suppressions should be added [here](mirth-channel-code/conf/owasp-suppress.xml).

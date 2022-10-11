# DEV-DiagramChannel-Writer

__Channel Export__ - Tue Mar 29 2022 17:13:09 GMT-0000 (UTC)

__Deploy Script__

```
// This script executes once when the channel is deployed
// You only have access to the globalMap and globalChannelMap here to persist data
return;
```

__Undeploy Script__

```
// @apiinfo """Example only: Notes about calls out to APIs in other code, e.g. Kotlin.
// This script executes once when the channel is undeployed
// You only have access to the globalMap and globalChannelMap here to persist data
logger.info("Undeploy: Thank you for using this version of the DiagramChannel.");
return;
```

__Preprocessor Script__

```
// @apiinfo """Example only: Preprocesses channel message data."""
// Modify the message variable below to pre process data
return message;
```

__Postprocessor Script__

```
// @apiinfo """Example only: Responses are stored as "Postprocessor" in the response map."""
return;
```

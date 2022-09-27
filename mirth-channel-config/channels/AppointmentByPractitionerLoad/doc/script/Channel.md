# AppointmentByPractitionerLoad

__Channel Export__ - Wed Jun 22 2022 16:42:39 GMT-0000 (UTC)

__Deploy Script__

```
var channelService = Packages.com.projectronin.interop.mirth.channel.AppointmentByPractitionerNightlyLoad.Companion.create();
onDeploy(channelService)

```

__Undeploy Script__

```
// This script executes once when the channel is undeployed
// You only have access to the globalMap and globalChannelMap here to persist data
return;
```

__Preprocessor Script__

```
// Modify the message variable below to pre process data
return message;
```

__Postprocessor Script__

```
// This script executes once after a message has been processed
// Responses returned from here will be stored as "Postprocessor" in the response map
return;
```

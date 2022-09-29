# MDMQueueOut

__Channel Export__ - Tue Jul 19 2022 23:12:22 GMT-0000 (UTC)

__Deploy Script__

```
// This script executes once when the channel is deployed
// You only have access to the globalMap and globalChannelMap here to persist data

var channelService = Packages.com.projectronin.interop.mirth.channel.MDMQueueOut.Companion.create();
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

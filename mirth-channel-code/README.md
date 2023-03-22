# interop-mirth-code

## Writing a new channel
To write a wholly new channel, create a new calls in [the channel directory](src/main/kotlin/com/projectronin/interop/mirth/channel).
You'll want to extend an appropriate abstract class in [the base directory](src/main/kotlin/com/projectronin/interop/mirth/channel/base).
You'll also likely want to create a destination class extending from a destination abstract class.
The overridable function names correspond to the different steps in Mirth and you can implement as many or as few as are needed
for your use case.

After you've finished writing a new Kotlin class for a Mirth channel, you'll also want to make sure the corresponding
Mirth artifiacts are created in [mirth-channel-conifg](../mirth-channel-config).
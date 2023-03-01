# README

This repo contains Interop's Mirth/NextGen Connect channels.

## Gradle

This project uses Gradle to ensure a consistent deployment.

### Setup

There is some initial setup required to ensure that you can fully build this project.

1. Install [Docker](https://docs.docker.com/get-docker/).
2. Configure your environment for access to dev Seki:
    1. Add `SEKI_AUTH_CLIENT_ID` and `SEKI_AUTH_CLIENT_SECRET` environment variables to your machine.
    2. The appropriate values for these are the `client_id` and `client_secret` values
       found in [auth0 ronin-dev](https://manage.auth0.com/dashboard/us/ronin-dev/apis/6246324283cdc5003e797b3f/test)
       Interop Proxy Server API
3. Configure your environment for access to the local Aidbox:
    1. Add `AIDBOX_CLIENT_ID` and `AIDBOX_CLIENT_SECRET` environment variables to your machine.
    2. The appropriate values can be found in the current [.env](dev-env/.env) file.

### Tasks

The following Gradle tasks are currently exposed and can be
utilized by this project:

* __mirth__ - Stands up Mirth with the appropriate local environment. This includes all other tasks except __it__. For
  local dev environments, it can be helpful to delete the previous dev-env container before running `./gradlew mirth`.
* __copyMirthConnector__ - Installs the Mirth Connector JAR and reloads the resource. This requires a currently running
  Mirth instance.
* __installAllChannels__ - Installs all channels from the filesystem as currently defined in [channels](channels). This
  will create a base version matching the filesystem definition, and a tenant-based version prefixed by `ronin`.
* __installChannel__ - Installs the specific channel indicated by `--channel [CHANNEL]`. The provided channel name
  must match a directory found in [channels](channels).
* __updateTenantConfig__ - Updates the local tenant config based off of config properties files within each channel.
* __installAidboxResources__ - Installs resources needed for channels to run found in `aidbox` folder
  in [channels](channels)
* __it__ - Runs the integration tests. First run `./gradlew mirth` to stand up Mirth, then run `./gradlew it`

### Configuration

[build.gradle.kts](build.gradle.kts) contains the base configuration for the Gradle build. If further information is
needed about any possible configuration options, please consult
the [MirthExtension](buildSrc/src/main/kotlin/com/projectronin/interop/gradle/mirth/MirthExtension.kt).

## Docker

Detailed instuctions for using docker compose with this repo are in the `dev-env README.md`
[here](https://github.com/projectronin/interop-mirth-channels/blob/1eb260e3ac4572474a0498400a77eb38395cf600/dev-env/README.md)

## Mirth Connector

To update the Mirth Connector JAR to use with local Mirth:

1. Modify Mirth Connector and rebuild the JAR.
   See: [interop-mirth-connector](https://github.com/projectronin/interop-mirth-connector)
2. Run `./gradlew mirth` or `./gradlew copyMirthConnector`
3. Redeploy all channels in the currently running Mirth instance.

## Channel Subfolders

Under [channels](channels)

* [LocationLoad](channels/PatientLoad) - channel for loading the tenant locations
* [AppointmentLoad](channels/PatientLoad) - channel for loading the appointments for tenant locations
* [PatientLoad](channels/PatientLoad) - channel for loading the patients for tenant appointments
* [PractitionerLoad](channels/PractitionerLoad) - channel for loading the practitioners for tenant appointments
* [ConditionLoad](channels/ConditionLoadd) - channel for loading the conditions for tenant patients
* ... (and more)

Under [channels](channels)*/channelName*

* __channel__ - channel definition export file, can be imported - *channelName*.xml
* __doc__ - channel flow diagram and overview, generated from definition - *channelName*.html
* __aidbox__ - bundles needed for the channel to run
    * *name*.json
* __config__ - contains any local configuration properties files
    * `default.properties` contains the configuration used for the default tenant.
    * `[TENANT-MNEMONIC].properties` contains tenant-specific configuration for any other tenants.

Under [code-template-libraries](code-template-libraries)

* __Standard Functions.xml__ - code templates for common functions in channels
  are automatically imported to Mirth by gradle builds.

## Load a Channel

1. Ensure any tenant settings are in files in *channelName*/__config__
2. If there are extra cases you want to test, add that test data.
   See [here](https://github.com/projectronin/interop-mock-ehr/blob/master/init/README.md).
3. Use Gradle (as above) to load the channel.
4. Patch test data as desired. An example is applying "recent" dates to Appointments.
   See  [here](https://github.com/projectronin/interop-mock-ehr/blob/master/init/README.md).
5. In Mirth: Deploy, Enable, and Start the channel. It polls at its configured interval.

## Development Only

These subfolders provide files to build Mirth and test Mirth channels in a local development environment:

[dev-env](dev-env) - Files for setting up a local Mirth development environment using Gradle with this repo.

[dev-util](dev-util) - Utilities for Mirth channel developers. Over time, tools may be replaced or refactored to
increase automation.


* [channels](dev-util/channels) - Channels used only in development

    * [DEV-DiagramChannel-Writer](dev-util/channels/DEV-DiagramChannel-Writer) - transforms channel export XML into a
      flow
      diagram (SVG) and description (HTML)

    * [DEV-DiagramChannel-Writer](dev-util/channels/DEV-DiagramChannel-Writer)/__svg/SVGFullDiagram.html__
        - Archives the SVG code for the full channel flow diagram, without the color the XSLT adds.
        - The DiagramChannel inserts SVG snippets from this file into different XSLT steps.
        - This file is the only place a developer can view or modify the full drawing.
        - &lt;svg> is wrapped in &lt;html> for easy testing of SVG changes in a browser.

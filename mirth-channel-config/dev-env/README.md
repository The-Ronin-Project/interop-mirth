## Running Locally

The docker-compose.yaml is intended to set up and run everything required to start a local Mirth Connect instance and
all of the dependent services and resources (devbox, database, etc.) with minimal local set up. 

### Required Configuration

The [.zshenv.example] contains the variables needed to be added to your local environment if not already set.

1. Aidbox Devbox Licence, see [here](https://docs.aidbox.app/getting-started/installation/setup-aidbox.dev)
2. App Orchard Sandbox access,
   see [here](https://projectronin.atlassian.net/wiki/spaces/ENG/pages/1620279305/Uses+for+AppOrchard+in+Development#AO_SANDBOX_KEY-for-.env-and-.zshenv-Files)
3. Access to dev Seki, see [here](https://github.com/projectronin/interop-mirth-channels#readme)

More detailed instuctions are included on
   our [wiki](https://projectronin.atlassian.net/wiki/spaces/ENG/pages/1687552027/Development+Environment)

## Start Up

Once configured, docker compose will do all the work. With Docker running on the machine just run the following in this
directory.

```bash
docker compose up
```

### Updating Docker Image Cache

Since our docker compose is making use of the 'latest' tag for a number of projects it will require manual updates to
ensure your cache has the latest instance of a particular image. The below command can be run to update the local docker
cache.

```bash
docker compose pull
```

### Accessing Services

1. Mirth Connect will be available at https://localhost:8443,
   see [here](https://projectronin.atlassian.net/wiki/spaces/ENG/pages/1595867140/Mirth#Create-a-Mirth-Admin-UI-connection)
   for more details.
2. Aidbox (devbox) console will be available at http://localhost:8888/ui/console
3. Mock EHR will be available at https://localhost:8081, see [here](https://github.com/projectronin/interop-mock-ehr)

### Test Data

After you build Mirth locally, test data is available from the Mock EHR on port 8081 for a test tenant called "ronin".

* You can immediately use the supplied Mock EHR data. It has structure and relationships
  described [here](https://github.com/projectronin/interop-mock-ehr/blob/master/init/README.md)
* You can modify and add resources to the Mock EHR data sets before and after building the Mirth dev-env.
  See [here](https://github.com/projectronin/interop-mock-ehr/blob/master/init/README.md)
* You can view and patch Mock EHR data after building the dev-env, for example to make date values recent.
  See [here](https://github.com/projectronin/interop-mock-ehr/blob/master/init/README.md)

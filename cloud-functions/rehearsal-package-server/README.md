Initialization:

1. Fetch dependencies: `yarn install`
2. Build: `yarn run build`
3. Create archive: `yarn run zip`
4. Initialize: `bx wsk action create rehearsal-package-server --kind:nodejs8 rehearsal-package-server.zip --web true --param apiKeyId <KEY> --param serviceInstanceId <INSTANCE>`

To update: `yarn run build && yarn run update`
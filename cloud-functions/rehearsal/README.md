1. Install Node packages: `cd action && yarn install`
2. Build `rehearsal.jar` and copy it into `action/`
3. Create the action:  `bx wsk action create rehearsal --docker=arjunguha/rehearsal --web=true`
4. To update the action: `./update.sh`
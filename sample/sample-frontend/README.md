# sample-frontend

This project is a sample React web application.

## Prerequisites Required for Running App

The latest version of [Node.js](https://nodejs.org/en/download/package-manager/) must be installed.

## Run the App in Local Environment

Run following commands in the terminal.

   ```bash
   npm install
   npm start
   ```

Then, open [localhost:8080](http://localhost:8080) in a browser

## Build the App for Production

Run following commands in the terminal.

```bash
npm install
npm run build
```

## Running Tests

```bash
npm test
```

## How to Use the Project

The project has following structure:

```bash
.
├── src
│   ├── assets
│   ├── components
│   ├── config
│   ├── contexts
│   ├── hooks
│   ├── models
│   ├── routes
│   ├── utils
├── test
├── types
```

* `assets`: This directory contains all the files (images, audio etc) that are used by the UI component.
* `components`: This directory contain all the components created for this UI application.
* `config`: This contain the application specific configurations.
* `contexts`: This contain contexts like LocationServiceContext to pass and share data to nested react conponents.
* `hooks`: This contain helper hooks.
  * `useAuth.tsx` This file contain auth related helper hooks and exposes login, logout and auth constants.
  * `useQuery.tsx` This file contain hooks to query data asynchronous and expose other constants like loading, error to track query state.
* `routes`: This contain route related files.
  * `Routes.tsx` This file uses react-router to describe frontend routes for this application.
  * `ProtectedRoute.tsx` This file contain auth protected frontend routes.
* `utils`: This contain common utilities.
  * `Http.ts` has generic helper functions written over fetch API to do GET, POST requests.
  * `api.ts` This file uses `Http.ts` and provide application specific functions to do POST requests.
  * `resolveBackend.ts` This file contain helper function to resolve location of backend using location service.
* `test`: This directory contains all the tests for the UI application.
* `types`: This directory contains all the types that needs to be imported externally for UI application.

## References

* ESW-TS Library - [Link](https://tmtsoftware/esw-ts/)
* ESW-TS Library Documentation - [Link](https://tmtsoftware.github.io/esw-ts/)

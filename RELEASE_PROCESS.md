# The Surfer Release Process

- Create a new local feature branch, e.g. `git checkout -b feature/bumpversion-to-v0.1.2`
- Use the `bumpversion.sh` script to bump the project version. You can execute the script using {major|minor|patch} as first argument to bump the version accordingly:
  - To bump the patch version: `./bumpversion.sh patch`
  - To bump the minor version: `./bumpversion.sh minor`
  - To bump the major version: `./bumpversion.sh major`
- Commit the changes to the feature branch.
- Push the feature branch to GitHub.
- Make a pull request from the just-pushed branch.
- Wait for all the tests to pass!
- Merge the pull request into the `develop` branch.


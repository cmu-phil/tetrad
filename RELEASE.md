# Tetrad Release Checklist

Use this checklist for every public release (major, minor, or patch).

This is a template 2025-6-15 but I will adjust it to match reality soon.

## Versioning and Tagging

- [ ] Update `pom.xml` version number
- [ ] Update version number in `about.properties` or equivalent (if applicable)
- [ ] Commit with message: `Prepare release vX.Y.Z`
- [ ] Create a signed Git tag: `git tag -s vX.Y.Z -m "Release vX.Y.Z"`
- [ ] Push tag to GitHub: `git push origin vX.Y.Z`

## Testing

- [ ] Run full test suite: `mvn test`
- [ ] Run serialization compatibility test:
    - [ ] Verify `testLoadability()` passes
    - [ ] Run `doArchive()` and inspect contents of `archives/`
- [ ] Confirm `serialVersionUID = 23L` on all serializable classes
- [ ] Run example end-to-end session save/load (manual check)

## Documentation

- [ ] Update `CHANGELOG.md` with highlights
- [ ] Verify Javadoc builds cleanly: `mvn javadoc:javadoc`
- [ ] Ensure key public APIs are documented

## Deployment

- [ ] Run `mvn clean deploy -P release` to deploy to Maven Central
- [ ] Publish updated documentation if separate
- [ ] Update website (if applicable)

## Post-Release

- [ ] Bump version to next snapshot: `X.Y.(Z+1)-SNAPSHOT`
- [ ] Commit and push
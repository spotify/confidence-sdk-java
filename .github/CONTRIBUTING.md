## Contributing

### Commit and PR linting
We require pull request titles to follow the Conventional Commits specification and we also encourage individual commits to adher to that.

We use "squash merge" and any merge PR title will show up in the changelog based on the title.

### Testing
We ask you to write well covered unit tests with your changes. To run tests:
```shell
mvn verify
```
As part of the CI pipeline, tests will run on java version 11 and 17.

### Formatting

Please make sure you format your code according to [google-java-format](https://github.com/google/google-java-format) before making a PR. 
There are CI checks using [fmt](https://github.com/spotify/fmt-maven-plugin) that will fail otherwise.

You can use `fmt` to format your code using:
```shell
mvn com.spotify.fmt:fmt-maven-plugin:format
```

### Signing
Maven is configured to sign the generated artifacts using GPG. This is a security measure required for uploading to Maven Central.

Signing passphrases are securely stored in Github's CI, but the signing operation is not needed when developing locally and can always
be skipped via the argument `-Dgpg.skip`

### Releasing
Github Actions are set up that are able to:
- Create automated PRs to manage new Github tags/releases
- Manage versioning automatically (including the `pom.xml` file)
- Upload the generated artifacts to Maven Central **Staging**

In order to promote an uploaded version from **Staging** to **Release** (hence making it openly available on [Maven Central Search](https://central.sonatype.com/)) a user with the right credentials must login into the the [Sonatype UI](https://oss.sonatype.org/#welcome) and perform the release process manually.

#### After realeasing
After a release PR is merged, the main branch will stay at the release version (non-snapshot) until updated. Release please will create a PR ([example](https://github.com/spotify/confidence-sdk-java/pull/55)) that does this "snapshot bump". The recommendation is to merge that PR directly when possible.

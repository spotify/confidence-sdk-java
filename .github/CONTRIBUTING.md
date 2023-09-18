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

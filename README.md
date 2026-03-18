# Fibricheck Android Camera SDK
The Android Camera SDK allows developers to integrate FibriCheck's heart rhythm analysis technology into their own application. The SDK interfaces with the smartphone's camera and generates a raw PPG signal and a rough heartrate estimation in beats per minute.

This SDK should be used in conjunction with the FibriCheck Cloud. It only implements data acquisition and does not offer any stand-alone rate or rhythm diagnostic capabilities.

## How to include

### Add the dependency using the GitHub Package Registry

The Android Camera SDK package is hosted on GPR (GitHub Package Registry). You need to authenticate using a GitHub username and personal access token to access the package in your project. For more information, see ("Creating a personal access token")[https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token] in the GitHub documentation.

Include the following configuration in your `build.gradle` file: 

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/fibricheck/android-camera-sdk")
        credentials {
            username = gprUser ?: System.getenv("GITHUB_USERNAME")
            password = gprKey ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

The dependency can then be added:

```groovy
dependencies {
    implementation 'com.qompium.fibricheck:camerasdk:x.y.z'
}
```

### Add the dependency using JitPack
[JitPack](https://jitpack.io) is a package repository for Git and allows to add dependencies without having to authenticate (as with the GitHub package registry):

Include the following configuration in your `build.gradle` file:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency in the following way:
```groovy
dependencies {
        implementation 'com.github.fibricheck:android-camera-sdk:x.y.z'
}
```

### In your code
Once the dependency is correctly added, the SDK is available in your code.

```java
import com.qompium.fibricheck.camerasdk.*;
```

## Running Tests

```bash
# All tests (SDK + test sequence)
./gradlew test

# SDK tests only
./gradlew :camerasdk:test

# Test sequence tests only
./gradlew :test-sequence:test
```

## Generate changelog

This project uses [git-cliff](https://git-cliff.org/) to generate changelogs following the [Keep a Changelog](https://keepachangelog.com/) format.

```bash
# Update CHANGELOG.md
git-cliff --output CHANGELOG.md

# Preview unreleased changes
git-cliff --unreleased
```

## Releasing a new version
To release a new version, follow the [git convention](https://www.conventionalcommits.org/en/v1.0.0/#summary) guidelines.
When a new PR to the `main` branch is merged, it will trigger the release process.
Development releases will be build on PR merged to the `dev` branch

## Logged Data Structure
When a `log` flag is enabled on a `CameraSettingsInput` and its corresponding mode is set to `auto`, the measurement result will include a `camera_settings` object containing the relevant log.

The log lists only add a new entry when the value differs from the previous one (or exceeds 0.001 for floating-point values). This avoids storing redundant data for values that remain stable across many frames.

The general structure of a log is:
```
[[<value>, <frame index>], ...]
```

**Example — focus distance:**
```json
[[0.0, 0], [0.1, 13], [0.5, 40]]
```
- Frame 0: focus distance is `0.0`
- Frame 13: changes to `0.1`
- Frame 40: changes to `0.5`, then remains constant for the rest of the recording

**White balance** is the exception, it uses three values (`r`, `g`, `b`) per entry:
```
[[<r>, <g>, <b>, <frame index>], ...]
```

**Data types per field:**
| Field | Structure | Notes |
|---|---|---|
| `iso` | `[[int, int]]` | |
| `exposure_time` | `[[long, int]]` | |
| `focus` | `[[float, int]]` | |
| `white_balance` | `[[float, float, float, int]]` | |
| `hdr` | `[[int, int]]` | 1 = on, 0 = off |

## License
This SDK is proprietary. See `LICENCE` for more information.
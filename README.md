# Fibricheck Android Camera SDK
The Android Camera SDK allows developers to integrate FibriCheck's heart rhythm analysis technology into their own application. The SDK interfaces with the smartphone's camera and generates a raw PPG signal and a rough heartrate estimation in beats per minute.

This SDK should be used in conjunction with the FibriCheck Cloud. It only implements data acquisition and does not offer any stand-alone rate or rhythm diagnostic capabilities.

**Important Compliance Notice!** This is an alpha release of the standalone FibriCheck Camera SDK for Android. This repository is not yet certified within our quality management systems to be used in production environments. It can currently only be used for development/testing purposes.

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

<!-- x-release-please-start-version -->
```groovy
dependencies {
    implementation 'com.qompium:fibricheck-camera-sdk:0.3.2'
}
```
<!-- x-release-please-end -->

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
<!-- x-release-please-start-version -->
```groovy
dependencies {
        implementation 'com.github.fibricheck:android-camera-sdk:v0.3.2'
}
```
<!-- x-release-please-end -->

### In your code
Once the dependency is correctly added, the SDK is available in your code.

```java
import com.qompium.fibricheck.camerasdk.*;
```

## License
This SDK is proprietary. See `LICENCE` for more information.
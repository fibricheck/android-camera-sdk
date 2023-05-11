# Fibricheck Android Camera SDK
The Android Camera SDK allows developers to integrate FibriCheck's heart rhythm analysis technology into their own application. The SDK interfaces with the smartphone's camera and generates a raw PPG signal and a rough heartrate estimation in beats per minute.

**Important Compliance Notice!** This is an alpha release of the standalone FibriCheck Camera SDK for Android. This repository is not yet certified within our quality management systems to be used in production environments. It can currently only be used for development/testing purposes.

## How to include

### Add the dependency

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
    implementation 'com.qompium:fibricheck-camera-sdk:0.2.0'
}
```
<!-- x-release-please-end -->

### In your code
Once the dependency is correctly added, the SDK is available in your code.

```java
import com.qompium.fibricheck_camera_sdk.*;
```

## License
This SDK is proprietary. See `LICENCE` for more information.
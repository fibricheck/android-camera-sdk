# Fibricheck Android Camera SDK


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

```groovy
dependencies {
    implementation 'com.qompium:fibricheck-camera-sdk:0.0.2'
}
```

### In your code
Once the dependency is correctly added, the SDK is available in your code.

```java
import com.qompium.fibricheck_camera_sdk.*;
```
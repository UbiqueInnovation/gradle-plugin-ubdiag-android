# UbDiag Gradle Plugin for Android

For seamless interplay between apps and the Ubique build management tools.

[![Build](https://github.com/UbiqueInnovation/gradle-plugin-ubdiag-android/actions/workflows/build.yml/badge.svg)](https://github.com/UbiqueInnovation/gradle-plugin-ubdiag-android/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ch.ubique.gradle/ubdiag-android.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22ch.ubique.gradle%22%20AND%20a:%22ubdiag-android%22)

## Features

### AndroidManifest.xml `<meta-data>` properties

The following `<meta-data>` properties are injected into the AndroidManifest.xml:

- `ub_buildid`: Unique build ID
- `ub_buildnumber`: Incremental build number
- `ub_branch`: Checked out Git branch of the source code
- `ub_flavor`: Gradle flavor of the build

### Launcher icon label

The build tool can draw a label banner onto the launcher icon (as per the manifest). By default the flavor's name is used for the label. The label for the 'prod' flavor is set to an empty string, i.e. it does not have a label.

A custom label can be set with the `launcherIconLabel` property within a Gradle flavor:

    myflavor {
        launcherIconLabel = "foobar"
    }

To disable the label banner, set it to an empty string:

    otherflavor {
        launcherIconLabel = ""
    }

If a `webicon` file name is provided via a custom Gradle property, the 'web icon' (PNG file in the app module directory) also gets a banner applied (only relevant for Shankar).

This feature requires the [ImageMagick command line tools](https://imagemagick.org/script/download.php) to be installed on the app's build system.

### Other configurations

Custom build directory, if set by a Gradle property (`-PbuildDir=foo`):

    project.buildDir = project.property("buildDir")

## Usage

To apply the Gradle build plugin, you have to add it as a project dependency in the top-level build.gradle:

    buildscript {
        repositories {
            mavenCentral()
            ...
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:4.2.2'   // Android build plugin
            classpath 'ch.ubique.gradle:ubdiag-android:4.2.0'  // Ubique build plugin
        }
    }

Then apply the plugin in the app's module build.gradle:

    apply plugin: 'com.android.application'  // standard Android app build plugin first
    apply plugin: 'kotlin-android'           // Kotlin plugin (if needed)
    apply plugin: 'ch.ubique.gradle.ubdiag'  // apply Ubique build plugin last
    
    android {
        ...
    }

### Build variables

The following build properties can be specified using the `-P` arguments:

    buildid         - used for "ub_buildid" meta data, a unique string to identify the build, defaults to "localbuild"
    buildnumber     - used for "ub_buildnumber" meta data, an incremental build number (per branch), defaults to "0"
    branch          - Git branch used for "ub_branch", defaults to the currently checked out branch, or "develop" if no Git
    buildDir        - custom build directory path, optional
    webicon         - web icon file to be generated and labelled, optional

## Development & Testing

To test any changes locally, you can deploy a build to your local maven repository and include that from any application:

1. Define a custom version by setting the `VERSION_NAME` variable in the `gradle.properties` file.
2. Deploy the plugin artifact by running `./gradlew publishToMavenLocal`
3. Reference the local maven repository in your application's build script: 

        repositories {
            mavenLocal()
        }

4. And apply the local plugin version:

        dependencies {
            classpath "ch.ubique.gradle:ubdiag-android:$yourLocalVersion"
        }

## Deployment

Each release on Github will be deployed to Maven Central.

Use the `VERSION_NAME` as defined in the `gradle.properties` file as the release tag, prefixed with a `v`.

* Group: `ch.ubique.gradle`
* Artifact: `ubdiag-android`
* Version: `major.minor.revision`, with `major.minor` being in sync with the Android Gradle Plugin (AGP).

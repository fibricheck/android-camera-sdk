# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-04-24

### Added
- Camera settings and log (FCRN-2108,FCRN-2109) ([45d6709](https://github.com/fibricheck/android-camera-sdk/commit/45d67090cde66e9f1118810e0964a89a0c0c67b0))
- Added get label function (FCS-78) ([2f262db](https://github.com/fibricheck/android-camera-sdk/commit/2f262db64ed3af120e800a33e14ba6e9ded9612e))
- Testing of the sequence tester ([257eb3c](https://github.com/fibricheck/android-camera-sdk/commit/257eb3c0d387fe0dd780b5da7f05732eda8e4e1c))
- Added hdr config (FCS-84) ([38dae8f](https://github.com/fibricheck/android-camera-sdk/commit/38dae8f2c876ba770ebf09b910ee8286a725128d))

### Changed
- Added tests to verify build ([1dd5094](https://github.com/fibricheck/android-camera-sdk/commit/1dd50949cd145d7fa11356a1848de245425061d5))
- Refactor build process and add testing ([3bd0e79](https://github.com/fibricheck/android-camera-sdk/commit/3bd0e79a9180ca3a9a9caf5738b674e702146cda))
- Prepare for dev release ([3a0ddd6](https://github.com/fibricheck/android-camera-sdk/commit/3a0ddd6e03f845e0b6a56cf314056174b062784e))
- Reformat ([ea7a295](https://github.com/fibricheck/android-camera-sdk/commit/ea7a29598a2da77625339ce2a4137977203a6df0))
- Bumped version, project cleanup (FCS-84) ([eb6d844](https://github.com/fibricheck/android-camera-sdk/commit/eb6d844f4ef5d7e953744966a5abba37b87f2140))
- Logging (FCS-84) ([60d84a3](https://github.com/fibricheck/android-camera-sdk/commit/60d84a3845b0e0dc08b32dd57c428088040daebc))
- Added log info to readme (FCS-84) ([c240737](https://github.com/fibricheck/android-camera-sdk/commit/c2407376a9d4a7ba6fdeea3591dd3e4e9d677143))
- Updated defaults (FCS-85) ([86faf6a](https://github.com/fibricheck/android-camera-sdk/commit/86faf6a061899a0b1906143e56c75234a63bb408))
- Updated hdr & focus logging + sequence tester (FCS-85) ([b688022](https://github.com/fibricheck/android-camera-sdk/commit/b6880228bef486b9274d6dbf23a90d67e1b439aa))

### Fixed
- Clear listeners before making new ones (FB-788) ([3395100](https://github.com/fibricheck/android-camera-sdk/commit/3395100cd44bf44a5404b7497f2bd2ee09cd2ae8))
- Updated gradle build script to use java 17 (FB-788) ([5a5a9a0](https://github.com/fibricheck/android-camera-sdk/commit/5a5a9a0230fc30cfb4785969d8cecebb7c95aa1b))
- Manifest fix (FB-788) ([bf89c1e](https://github.com/fibricheck/android-camera-sdk/commit/bf89c1e0b85a136ed607dff38c0e1ea597eaff54))
- Dont add white balance if empty (FCRN-2108) ([fd77248](https://github.com/fibricheck/android-camera-sdk/commit/fd77248eecfb01e2bc47312ca3a3f452cbb0809c))
- Pass GH config in workflow ([71c19fa](https://github.com/fibricheck/android-camera-sdk/commit/71c19fa5f64ec5f536624d14fe087c2e7b4f9183))
- FCS-78 fix wrong IFU URL ([9fd3b74](https://github.com/fibricheck/android-camera-sdk/commit/9fd3b74643b3b00d8e6a0bb47e86dc14af7b7412))

### Build
- Updated pipelines, added dev pipeline (FB-788) ([14e92de](https://github.com/fibricheck/android-camera-sdk/commit/14e92de2f0bccbac6a48d1c86454d5b7d22b7396))
- Updated jitpack (FCRN-2108) ([a4e096e](https://github.com/fibricheck/android-camera-sdk/commit/a4e096e925a47dff61adff295c806886577c60bc))

### Revert
- Reverted gradle versions for compat (FCS-84) ([b935cbf](https://github.com/fibricheck/android-camera-sdk/commit/b935cbfd988ac017d9fa3576b63cd6aa4da9bc08))

## [1.0.2] - 2024-12-13

### Changed
- Update regulatory documentation (#30) ([991ae08](https://github.com/fibricheck/android-camera-sdk/commit/991ae08460d5ad9c7c3fc674465e1df1dc2ce512))

## [1.0.1] - 2024-06-24

### Fixed
- Camera resolution sorting ([bfd1f69](https://github.com/fibricheck/android-camera-sdk/commit/bfd1f69cc2f183174907425fc7b631cf8dc2f9a3))

## [1.0.0] - 2023-09-25

### Changed
- Update example for easier regulatory verification/testing (#24) ([dbc6c87](https://github.com/fibricheck/android-camera-sdk/commit/dbc6c87a6ca35f0a83d6e27272bb87d2ca26f239))
- Bugfix in ci/cd pipelines ([abac87b](https://github.com/fibricheck/android-camera-sdk/commit/abac87bf1cfcc151ce02307ccbddd0d8dbcf37f6))
- Bugfix in ci/cd pipelines ([5211579](https://github.com/fibricheck/android-camera-sdk/commit/5211579b6282274914de3c7161bde6deeffbc777))
- Bugfix in ci/cd pipelines ([0ad73d2](https://github.com/fibricheck/android-camera-sdk/commit/0ad73d231199612682a5d1e82acb00c625654833))
- Bugfix in ci/cd pipelines ([ace71e2](https://github.com/fibricheck/android-camera-sdk/commit/ace71e20fcd928952f907d49b60db1b1a67b6dd4))
- Bugfix in ci/cd pipelines ([ecd2f5d](https://github.com/fibricheck/android-camera-sdk/commit/ecd2f5d3752df3b3e6889169475271eab94cc8a3))

## [0.4.1] - 2023-08-18

### Fixed
- Make timing more accurate for received frames ([b557104](https://github.com/fibricheck/android-camera-sdk/commit/b557104ace2a2b430377400016859a36a8f18512))

## [0.4.0] - 2023-06-07

### Added
- Change package naming to comply with Android guidelines (#19) ([ad7ff32](https://github.com/fibricheck/android-camera-sdk/commit/ad7ff3240177680f064143a6a8e9fcab90cb448d))

## [0.3.2] - 2023-06-01

### Changed
- Fix typo in README for jitpack config (#15) ([9834cf6](https://github.com/fibricheck/android-camera-sdk/commit/9834cf6003eafe9d241ce6633b992f70feb3a7d1))
- Add regulatory documentation using the lifecycle tool (#12) ([f307117](https://github.com/fibricheck/android-camera-sdk/commit/f3071179f6720c3290b88b0042069d1797d6bc33))

### Fixed
- Add serialized annotations for Flutter implementations (#16) ([fe153f0](https://github.com/fibricheck/android-camera-sdk/commit/fe153f07002e79a8d09f0bc4d96387bd22825028))
- Fix nullpointer issue on sensorListener (#11) ([f5e43e8](https://github.com/fibricheck/android-camera-sdk/commit/f5e43e8b24887eb0f279c3fd2e5dcb2118be6946))

## [0.3.1] - 2023-05-23

### Changed
- Add diagnostic warning to README ([a0e6da1](https://github.com/fibricheck/android-camera-sdk/commit/a0e6da1093a7ef222db5934a83d3bc4c4210b706))
- Include jitpack as a build output ([8f0e6f6](https://github.com/fibricheck/android-camera-sdk/commit/8f0e6f651570b2f5ac12f80c51d39981e3d47a38))

## [0.3.0] - 2023-05-11

### Added
- Clean up versioning issues with setting up release please, bumping to 0.3.0 ([fd2a25a](https://github.com/fibricheck/android-camera-sdk/commit/fd2a25a18b286a546050184929f5a1b4e7d243bc))

### Fixed
- Fix bug in ci/cd that caused the package to not be published ([64959f9](https://github.com/fibricheck/android-camera-sdk/commit/64959f91373336633f7d994e0f6a953350d47c46))
- Bugfixes in ci/cd pipelines ([6e96773](https://github.com/fibricheck/android-camera-sdk/commit/6e96773e6573331781337cf4191a3b62f8e2aba9))

## [0.2.0] - 2023-05-11

### Added
- Change timeRemaining to onTimeRemaining ([a12c101](https://github.com/fibricheck/android-camera-sdk/commit/a12c101c0476ab34ce6202059d3841466e1dc6c7))

### Fixed
- Fix typo in ci/cd that prevented package to be build ([1deadfb](https://github.com/fibricheck/android-camera-sdk/commit/1deadfb01da4854fd2d959299f208211465d304d))

## [0.1.3] - 2023-05-11

### Fixed
- Typo in annotations for release please to detect versions in README.md ([5a9545b](https://github.com/fibricheck/android-camera-sdk/commit/5a9545bed0826589ee90c5baa782e26eba7bf8b6))
- Enable automatic publishing of packages using release please ([cb3ef38](https://github.com/fibricheck/android-camera-sdk/commit/cb3ef38b77414aa7f977192e33f90ca91b126ccb))

## [0.1.2] - 2023-05-11

### Changed
- Include release please for release automation ([9c92c69](https://github.com/fibricheck/android-camera-sdk/commit/9c92c694f2ac358bbee67fe1dc014093519dc17a))
- Add license clarification ([d930e1f](https://github.com/fibricheck/android-camera-sdk/commit/d930e1f67a1b9d0ebfeeebd23d73813a389e84f0))


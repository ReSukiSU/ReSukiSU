## Translations

English and Chinese Simplified are maintained by the developer. If you find inappropriate wording or missing translations, please open an issue or submit a pull request with details.

For languages other than English and Chinese Simplified, please don't create pull requests for translations; instead, use [Crowdin](https://crowdin.com/project/ReSukiSU).

## Reporting bugs

Before reporting a bug, please first read the [document](https://resukisu.github.io)

If the issue still exists, please ensure you have tried the **latest CI build**, as the bug may have already been resolved. 

**You can export log from `Settings` page**

When you [Open an issue](https://github.com/ReSukiSU/ReSukiSU/issues), you **must**:

- Select right issue template
- Ensure it doesn't duplicate with current issues.
- Describe the bug clearly, including the exact steps to reproduce it.
- **Attach the exported log file** from the latest build.

*(Note: Issues lacking necessary logs or clear reproduction steps may be closed.)*

## Suggesting features

[Open an issue](https://github.com/ReSukiSU/ReSukiSU/issues) describing the feature you want and your reason for it.

## Code

Fork this repository, make your changes, and open a pull request.

Please note:

- Describe clearly what your changes do and the problem they solve.
- In the pull request description, specify the device(s) and Android version(s) on which the changes were tested.
- Follow the existing coding style and project conventions.
- Make sure the project builds successfully before submitting the pull request.
- If your code related manager, format your code using Android Studio and optimize imports before committing.
- Signed off your changes
- make sure your commit's title is look like `part: change`

## If it are an commit cherry-pick from upstream, you should:

- Mark what commit you cherry-picked
- Process any mention of issues/pull request
- Example:
  
>- if the upstream commit message is
```
        kernel: patch from upstream (#114514)

        This is the detailed description of the patch
        Fix #1919810

        Signed-off-by: Fred Jones <fred.jones@foo.org>
```
>- then Joe Smith would upload the patch for ReSukiSU as
```
        kernel: patch from upstream (https://github.com/tiann/KernelSU/pull/114514)

        This is the detailed description of the important patch
        Fix https://github.com/tiann/KernelSU/pull/1919810

        [cherry-picked from upstream commit https://github.com/tiann/KernelSU/commit/a57a7913f53e34c8a8d905444b126b3316146e69]
        Signed-off-by: Fred Jones <fred.jones@foo.org>
        Signed-off-by: Joe Smith <joe.smith@foo.org>
```

## Requirements for revert patches:

- Add a reason for the revert
- Do not delete or modify the revert information that is generated when using
`git revert`
- If modifications have been made after creating the revert, include a list of
these in the commit message
- Example:
```
        Revert "kernel: fix a bug in foobar.c"

        This reverts commit a57a7913f53e34c8a8d905444b126b3316146e69.

        Reason for revert: Some oppo devices failed memcmp check

        Additional modifications: Resolved merge conflicts

        Signed-off-by: Joe Smith <joe.smith@foo.org>
```

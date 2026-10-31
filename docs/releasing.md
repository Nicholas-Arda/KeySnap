# Release and hotfix workflow

The main checkout stays on `master` for the next feature release. Both the core repository
and the private `pro/` repository have a local `stable` branch pointing to the live release.
Never merge feature development into `stable` to fix an older release.

## Freeze before uploading

1. Commit the shipping sources in both repositories. The owner makes these commits.
2. Build the signed production AAB from those commits, using the Pro edition and the usual
   Android Studio signing flow. Do not change source files between building and freezing.
3. Run from the project root:

   ```powershell
   python -B tools/release_snapshot.py snapshot --aab app/release/app-release.aab
   ```

The tool reads the bundle's package/version, rejects unsigned-marker-free and `.dev` bundles,
checks that the source version agrees, and refuses uncommitted shipping sources. It saves
the AAB, core/Pro source ZIPs, local Gradle wrapper files, commit IDs and SHA-256 hashes under
`KeySnap/Yayinlar/<version>-code<code>/`. Both repositories get the same immutable local
`play/<version>-code<code>` tag. No commit, push, signing, upload or publication is performed.

The archive contains private Pro source. It must stay outside public Git tracking. The local
vault is excluded on the owner's workstation; another clone can use `--output` with a directory
outside the checkout. Signing keys and `local.properties` are not copied.

The tool verifies bundle identity and archive integrity, not the signing certificate or that an
arbitrary supplied binary was compiled from the recorded commits. Freeze immediately after
the signed build. `--historical --core-ref <sha> --pro-ref <sha>` records an explicitly labelled
reconstruction of a past release, rather than claiming original build provenance.

4. Upload **the archived `app.aab`**, retaining the existing managed-publishing routine.
   Further development can continue on `master` while that frozen package waits for release.
5. After the owner confirms the package is actually live:

   ```powershell
   python -B tools/release_snapshot.py mark-live --snapshot KeySnap/Yayinlar/1.1.7-code17 --published
   ```

Replace the example directory with the release actually published. This checks the archive,
moves both local `stable` refs and updates `KeySnap/Yayinlar/live.json`. It does not operate
Play Console. Never mark a merely approved or queued release live. A checked-out `stable`
branch cannot be moved; no checkout or stash is performed.

## Urgent fix to the live release

1. Read `live.json` and verify both `stable` refs match its core/Pro commits.
2. Use a separate workspace and a `hotfix/<version>` branch based on those commits in **both**
   repositories. Leave the main development checkout, its edits and its Pro checkout alone.
3. Apply only the necessary fix. Test it against that older source, including the optimized
   build and relevant phone behavior. A fix tested only on the next feature release is insufficient.
4. Choose an unused `versionCode` greater than every code uploaded to Play or archived locally.
   A visible name such as `1.1.6.1` can identify the maintenance release.
5. Build, freeze, submit and publish that small update. Then mark that snapshot live and carry
   the fix into development, resolving differences without importing unrelated new features.

If a queued feature AAB has a lower code than the hotfix, rebuild that feature release with
a higher code and submit it again. Its visible version name can stay the same. A hotfix still
passes through Google's review; this workflow separates source scope, not review time.

Keep release notes and verification evidence with each frozen release. The archive tool
does not claim that any tests ran. Its local checks can be run with:

```powershell
python -B -m unittest discover -s tools/tests -p test_release_snapshot.py -v
```

#!/bin/sh
# Refuses a commit that changes :bridge behaviour without bumping BridgeProtocol.VERSION.
#
# The bridge outlives the app: it has no parent/child relationship with the app process, so an
# old bridge binary keeps answering the control port across a reinstall or an update, forever,
# until something kills it. BridgeController.detectExistingBridge() is what kills it, and the
# only thing it compares is the reported protocolVersion against BridgeProtocol.VERSION. Skip
# the bump and a bug you just fixed keeps reproducing on every device whose bridge process was
# never killed -- with a build that contains the fix.
#
# History says this is worth automating: of the commits touching bridge/src, fewer than half
# bumped the version, and at least three changed bridge action behaviour without one.
#
# Installed as .git/hooks/pre-commit. Deliberate exception (a comment, a rename, a test-only
# change): commit with --no-verify.

set -e

PROTOCOL=bridge/src/main/java/com/example/bridge/BridgeProtocol.java

# Only main sources count; bridge tests run in the JVM and never reach a device.
changed=$(git diff --cached --name-only --diff-filter=ACMR -- bridge/src/main || true)
[ -n "$changed" ] || exit 0

# An added or changed line assigning VERSION is the bump.
if git diff --cached -- "$PROTOCOL" | grep -Eq '^\+.*VERSION[[:space:]]*='; then
    exit 0
fi

current=$(grep -Eo 'VERSION[[:space:]]*=[[:space:]]*[0-9]+' "$PROTOCOL" 2>/dev/null | grep -Eo '[0-9]+' || echo '?')

cat >&2 <<MSG

  Commit refused: :bridge sources changed without bumping BridgeProtocol.VERSION.

  Staged under bridge/src/main:
$(printf '    %s\n' $changed)

  $PROTOCOL still reads VERSION = $current.

  A bridge already running on a device is only replaced when the version it reports
  differs from this one. Leave it and the old binary keeps answering, so the change
  you just made will not take effect there -- including a bug fix.

  Bump it, or commit with --no-verify if this genuinely changes no behaviour.

MSG
exit 1

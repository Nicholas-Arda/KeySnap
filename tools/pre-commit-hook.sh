#!/bin/sh
# Thin wrapper: the check itself lives in tools/ so it is version-controlled and reviewable.
# .git/hooks is not tracked by git, so this file has to be re-created in a fresh clone:
#   cp tools/pre-commit-hook.sh .git/hooks/pre-commit   (or run tools/check_bridge_version.sh by hand)
exec "$(git rev-parse --show-toplevel)/tools/check_bridge_version.sh"

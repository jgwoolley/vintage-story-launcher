#!/usr/bin/env bash
set -euo pipefail

# jpackage rejects the non-ASCII apostrophe in the display name under a non-UTF-8 locale
export LC_ALL=C.UTF-8

command -v mvn >/dev/null || { echo "Maven not found. Install it (e.g. sudo apt install maven)." >&2; exit 1; }
command -v fakeroot >/dev/null || { echo "fakeroot not found (needed by jpackage --type deb). Install it: sudo apt install fakeroot" >&2; exit 1; }

# jpackage runs jlink, which needs the JDK's jmods (a separate package on Ubuntu/Debian)
JAVA_ROOT=$(dirname "$(dirname "$(readlink -f "$(command -v jpackage)")")")
[ -d "${JAVA_ROOT}/jmods" ] || {
  echo "No jmods found in ${JAVA_ROOT} (jlink fails with 'does not support linking from the current run-time image')." >&2
  echo "Install them, e.g.: sudo apt install openjdk-<version>-jmods" >&2
  exit 1
}

mvn clean verify

VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)

jpackage \
  --input target \
  --name "Nf3t’s VintageStory Launcher" \
  --linux-package-name "vintagestory-launcher" \
  --app-version "${VERSION}" \
  --icon icons/icon-256.png \
  --main-jar "vintagestory-launcher-${VERSION}.jar" \
  --main-class com.yelloowstone.vslauncher.Launcher \
  --type deb \
  --linux-shortcut \
  --linux-menu-group "Game" \
  --linux-app-category "Game" \
  --description "Launcher for Vintage Story" \
  --vendor "Nf3t"

DEB=$(ls -t vintagestory-launcher_*.deb | head -n 1)

echo
echo "Built ${DEB}. To install it:"
echo "  sudo apt install ./${DEB}"
echo
echo "If the same version is already installed, apt will skip it. Reinstall with either:"
echo "  sudo apt remove vintagestory-launcher && sudo apt install ./${DEB}"
echo "  sudo dpkg -i ./${DEB}"

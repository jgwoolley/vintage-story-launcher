mvn clean verify

jpackage \
  --input target \
  --name "vintagestory-launcher" \
  --main-jar vintagestory-launcher-0.1.1.jar \
  --main-class com.yelloowstone.vslauncher.Launcher \
  --type deb \
  --linux-shortcut \
  --linux-menu-group "Game" \
  --linux-app-category "Game" \
  --description "Launcher for Vintage Story" \
  --vendor "Yellowstone"

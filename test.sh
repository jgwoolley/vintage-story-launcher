# zipinfo -1 targets/vintagestory-launcher-0.1.1.jar
# unzip -p input/vintagestory-launcher META-INF/MANIFEST.MF

jpackage \
  --input targets \
  --name "vintagestory-launcher" \
  --main-jar vintagestory-launcher-0.1.1.jar \
  --main-class com.yelloowstone.vslauncher.App \
  --type dmg

# zipinfo -1 targets/vintagestory-launcher-0.1.1.jar
# unzip -p input/vintagestory-launcher META-INF/MANIFEST.MF
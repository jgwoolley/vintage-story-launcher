
APP_NAME="vslauncher-x64"

mkdir -p linux-resources
cp icons/icon-128.png "linux-resources/${APP_NAME}.png"

jpackage --input target \
    --dest output/ \
    --name "${APP_NAME}" \
    --main-jar vintagestory-launcher-0.1.1.jar \
    --main-class com.yelloowstone.vslauncher.Launcher \
    --type deb \
    --linux-shortcut \
    --linux-menu-group "Game" \
    --linux-app-category "Game" \
    --resource-dir linux-resources \
    --icon icons/icon-128.png

sudo apt install ./output/vslauncher-x64_1.0_amd64.deb

# dpkg -c output/vslauncher-x64_1.0_amd64.deb | grep -E '\.desktop|\.png|\.svg|\.ico'
# dpkg-deb -x output/vslauncher-x64_1.0_amd64.deb ./extracted_files
# ls /usr/share/applications/vslauncher-x64-vslauncher-x64.desktop
# sudo apt remove vslauncher-x64

# sudo update-desktop-database
# sudo touch /usr/share/icons/hicolor
# sudo gtk-update-icon-cache -f /usr/share/icons/hicolor
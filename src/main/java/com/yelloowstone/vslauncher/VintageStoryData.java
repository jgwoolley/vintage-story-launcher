package com.yelloowstone.vslauncher;

import java.io.File;

public class VintageStoryData {
    private final File path;

    public VintageStoryData(final File path) {
        this.path = path;
    }

    public File getPath() {
        return path;
    }

    @Override
    public String toString() {
        return this.path.toString();
    }
}

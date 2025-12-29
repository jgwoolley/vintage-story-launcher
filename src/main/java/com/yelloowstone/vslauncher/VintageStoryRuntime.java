package com.yelloowstone.vslauncher;

import java.io.File;

public class VintageStoryRuntime {
    private final File path;

    public VintageStoryRuntime(final File path) {
        this.path = path;
    }

    public File getPath() {
        return path;
    }
}

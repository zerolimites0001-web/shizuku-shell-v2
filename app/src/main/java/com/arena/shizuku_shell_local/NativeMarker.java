// MIT License - see LICENSE file.
package com.arena.shizuku_shell_local;

public final class NativeMarker {
    private NativeMarker() {}
    static { System.loadLibrary("shizuku_shell_local"); }
    public static native String nativeLibraryName();
}

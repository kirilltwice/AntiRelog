package dev.twice.antirelog.hooks;

public interface PluginHook {
    boolean isEnabled();
    void initialize();
    String getPluginName();
}

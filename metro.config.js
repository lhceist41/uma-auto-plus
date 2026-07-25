const { getDefaultConfig } = require("expo/metro-config")
const { withNativeWind } = require("nativewind/metro")
const path = require("path")
const fs = require("fs")

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname)

// Add resolver configuration.
config.resolver.alias = {
    "@": path.resolve(__dirname, "./"),
}

// Exclude Android build directories from Metro's file watcher to prevent ENOENT errors on Windows.
// These directories are created dynamically by Gradle and can cause Metro to crash when trying to watch them.
config.watchFolders = config.watchFolders || []
config.resolver = config.resolver || {}
config.resolver.blockList = [
    // Exclude Android build directories.
    /android\/app\/build\/.*/,
    /android\/build\/.*/,
]

// This checkout's node_modules is a symlink to the primary checkout (isolated worktree). Metro
// resolves modules against the project root and rejects the symlink target as "outside the
// project", so point resolution and the watch scope at the real directory. Build-time only.
let realNodeModules = path.resolve(__dirname, "node_modules")
try {
    realNodeModules = fs.realpathSync(realNodeModules)
} catch (e) {
    // Not a symlink (normal checkout): leave the default resolution alone.
}
if (realNodeModules !== path.resolve(__dirname, "node_modules")) {
    config.watchFolders = [...(config.watchFolders || []), realNodeModules]
    config.resolver.nodeModulesPaths = [path.resolve(__dirname, "node_modules"), realNodeModules]
}

module.exports = withNativeWind(config, { input: "./global.css", inlineRem: 16 })

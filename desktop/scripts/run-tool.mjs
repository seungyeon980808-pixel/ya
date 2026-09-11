import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const [tool, ...args] = process.argv.slice(2);
const entries = {
  vite: join(root, "node_modules", "vite", "bin", "vite.js"),
  vitest: join(root, "node_modules", "vitest", "vitest.mjs")
};

if (!tool || !(tool in entries)) {
  console.error("Usage: node scripts/run-tool.mjs <vite|vitest> [...args]");
  process.exit(2);
}

const env = { ...process.env };
// The hosted macOS sandbox blocks downloaded native helpers. Windows CI uses native esbuild.
if (process.platform === "darwin") {
  env.ESBUILD_BINARY_PATH = join(root, "node_modules", "esbuild-wasm", "bin", "esbuild");
}

const result = spawnSync(process.execPath, [entries[tool], ...args], {
  cwd: root,
  env,
  stdio: "inherit"
});

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}
process.exit(result.status ?? 1);

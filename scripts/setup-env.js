const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");

const examplePath = path.join(root, ".env.example");
const localPath = path.join(root, ".env.local");

if (!fs.existsSync(examplePath)) {
  console.error("Missing .env.example");
  process.exit(1);
}

if (fs.existsSync(localPath)) {
  console.log(".env.local already exists. No changes made.");
  process.exit(0);
}

fs.copyFileSync(examplePath, localPath, fs.constants.COPYFILE_EXCL);

console.log(".env.local created successfully!");

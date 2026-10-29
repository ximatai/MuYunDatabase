import { createHash } from "node:crypto";
import {
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const hashes = ["md5", "sha1", "sha256", "sha512"];

/** Inventory only the requested version; consumer repositories may retain other versions. */
export function inspectPublication({
  repository,
  group,
  version,
  artifacts,
  signed,
}) {
  if (!/^[a-zA-Z0-9_.-]+$/.test(group) || !/^[a-zA-Z0-9_.-]+$/.test(version))
    throw new Error("Invalid publication coordinates");
  if (!artifacts.length || new Set(artifacts).size !== artifacts.length)
    throw new Error("Publication artifacts must be non-empty and unique");
  const files = [];
  const modules = artifacts.map((artifact) => {
    if (!/^[a-zA-Z0-9_-]+$/.test(artifact))
      throw new Error("Invalid artifact ID");
    const relativeDirectory = `${group.replaceAll(".", "/")}/${artifact}/${version}`;
    const directory = join(repository, relativeDirectory);
    let fileVersion = version;
    if (version.endsWith("-SNAPSHOT")) {
      if (signed)
        throw new Error(
          "Maven Central release inventory cannot contain snapshots",
        );
      const metadata = readFileSync(
        join(directory, "maven-metadata.xml"),
        "utf8",
      );
      const timestamp = metadata.match(
        /<timestamp>(\d{8}\.\d{6})<\/timestamp>/,
      )?.[1];
      const build = metadata.match(
        /<buildNumber>([1-9]\d*)<\/buildNumber>/,
      )?.[1];
      if (!timestamp || !build)
        throw new Error(`Invalid snapshot metadata: ${relativeDirectory}`);
      fileVersion = `${version.slice(0, -"SNAPSHOT".length)}${timestamp}-${build}`;
    }
    const stem = `${artifact}-${fileVersion}`;
    const extensions = [".pom", ".module", ".jar", "-sources.jar", "-javadoc.jar"];
    const primaries = extensions.map((extension) => `${stem}${extension}`);
    const payloads = signed ? primaries.flatMap((name) => [name, `${name}.asc`]) : primaries;
    const expected = new Set(
      payloads.flatMap((name) => [
        name,
        ...hashes.map((hash) => `${name}.${hash}`),
      ]),
    );
    const actual = readdirSync(directory)
      .filter(
        (name) =>
          signed || name.startsWith(`${stem}.`) || name.startsWith(`${stem}-`),
      )
      .sort();
    for (const name of expected) {
      if (!actual.includes(name))
        throw new Error(
          `Missing publication file: ${relativeDirectory}/${name}`,
        );
    }
    let bytes = 0;
    for (const name of actual) {
      if (!expected.has(name))
        throw new Error(
          `Unexpected publication file: ${relativeDirectory}/${name}`,
        );
      const file = join(directory, name);
      const stat = lstatSync(file);
      if (!stat.isFile() || !stat.size)
        throw new Error(
          `Invalid publication file: ${relativeDirectory}/${name}`,
        );
      bytes += stat.size;
      files.push(`${relativeDirectory}/${name}`);
    }
    for (const name of payloads) {
      const content = readFileSync(join(directory, name));
      for (const hash of hashes) {
        const actualHash = readFileSync(
          join(directory, `${name}.${hash}`),
          "utf8",
        ).trim();
        if (actualHash !== createHash(hash).update(content).digest("hex"))
          throw new Error(
            `Checksum mismatch: ${relativeDirectory}/${name}.${hash}`,
          );
      }
    }
    return { artifact, files: actual.length, bytes };
  });
  if (signed) {
    // A Central bundle must not silently contain stale versions, metadata or extra artifacts.
    const expected = new Set(files);
    function walk(directory, prefix = "") {
      for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const name = `${prefix}${entry.name}`;
        if (entry.isDirectory()) walk(join(directory, entry.name), `${name}/`);
        else if (!entry.isFile() || !expected.has(name))
          throw new Error(`Unexpected bundle entry: ${name}`);
      }
    }
    walk(repository);
  }
  return {
    group,
    version,
    signed,
    deployments: 1,
    fileCount: files.length,
    bytes: modules.reduce((sum, module) => sum + module.bytes, 0),
    modules,
    files,
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repository, group, version, names, mode, output] =
    process.argv.slice(2);
  if (!output || !["signed", "unsigned"].includes(mode))
    throw new Error(
      "Expected repository group version artifacts signed|unsigned output",
    );
  const report = inspectPublication({
    repository,
    group,
    version,
    artifacts: names.split(","),
    signed: mode === "signed",
  });
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
  console.table(report.modules);
  console.log(
    `${report.version}: ${report.fileCount} files, ${(report.bytes / 1024 / 1024).toFixed(2)} MiB (uncompressed), one deployment`,
  );
  if (!report.signed)
    console.log(
      "Unsigned local inventory; signatures and their checksums add five files per primary in a Central release.",
    );
}

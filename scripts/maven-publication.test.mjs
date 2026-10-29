import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  mkdtempSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  renameSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { inspectPublication } from "./maven-publication-report.mjs";
import { waitForPublication } from "./wait-maven-central.mjs";

for (const entry of ["publishReleaseToSonatype", "publishToSonatype"]) {
  test(`real Gradle ${entry} graph has one gated upload followed by platform orchestration`, (t) => {
    const root = fileURLToPath(new URL("../", import.meta.url));
    const tag = `v${readFileSync(join(root, "build.gradle.kts"), "utf8").match(/version = "([0-9.]+)"/)[1]}`;
    const temp = mkdtempSync(join(tmpdir(), "muyun-release-graph-"));
    t.after(() => rmSync(temp, { recursive: true, force: true }));
    const init = join(temp, "verify-alias.gradle");
    writeFileSync(
      init,
      `gradle.projectsEvaluated {
    assert gradle.rootProject.tasks.getByName('publishToSonatype').actions.empty : 'Legacy plugin polling must be retired'
    assert gradle.rootProject.tasks.getByName('publishReleaseToSonatype').commandLine.last() == '--publish' : 'Standard release must enable validated-deployment publication'
  }`,
    );
    const graph = execFileSync(
      "./gradlew",
      [
        entry,
        "-Pmuyun.mavenCentralRelease=true",
        `-Prelease.tag=${tag}`,
        "--dry-run",
        "--console=plain",
        "--init-script",
        init,
      ],
      {
        cwd: root,
        encoding: "utf8",
        timeout: 120000,
        env: {
          ...process.env,
          SONATYPE_TOKEN: "",
          SONATYPE_PASSWORD: "",
          SIGNING_KEY_ID: "",
          SIGNING_SECRET_KEY: "",
          SIGNING_SECRET_KEY_BASE64: "",
          SIGNING_PASSWORD: "",
        },
      },
    );
    const tasks = graph
      .split(/\r?\n/)
      .filter((line) => line.endsWith(" SKIPPED"))
      .map((line) => line.slice(0, -" SKIPPED".length));
    assert.equal(
      tasks.filter((task) => task.endsWith(":2.uploadDeploymentDir")).length,
      1,
    );
    const upload = tasks.indexOf(":2.uploadDeploymentDir");
    for (const gate of [
      ":verifyReleaseCredentials",
      ":verifyReleaseTagVersion",
      ":verifyMavenCentralReleaseMode",
      ":verifyMavenCentralBundle",
    ])
      assert.ok(
        tasks.indexOf(gate) >= 0 && tasks.indexOf(gate) < upload,
        `Missing pre-upload gate: ${gate}`,
      );
    const platformPublish = tasks.indexOf(":publishReleaseToSonatype");
    assert.ok(platformPublish > upload);
    if (entry === "publishToSonatype")
      assert.ok(tasks.indexOf(":publishToSonatype") > platformPublish);
    else assert.ok(!tasks.includes(":publishToSonatype"));
    assert.ok(
      !tasks.includes(":bundleMavenCentralRelease"),
      "The uploader packages the validated directory; no preview ZIP is needed",
    );
  });
}

function publication(t, signed = true) {
  const repository = mkdtempSync(join(tmpdir(), "muyun-publication-"));
  t.after(() => rmSync(repository, { recursive: true, force: true }));
  const options = {
    repository,
    group: "net.example",
    version: "0.26.21",
    artifacts: ["library", "second-library"],
    signed,
  };
  for (const artifact of options.artifacts) {
    const directory = join(
      repository,
      "net/example",
      artifact,
      options.version,
    );
    mkdirSync(directory, { recursive: true });
    const extensions = [".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module"];
    for (const extension of extensions) {
      const file = join(
        directory,
        `${artifact}-${options.version}${extension}`,
      );
      const content = `fixture:${artifact}${extension}`;
      writeFileSync(file, content);
      if (signed) {
        const signature = "detached-signature-fixture";
        writeFileSync(`${file}.asc`, signature);
        for (const hash of ["md5", "sha1", "sha256", "sha512"])
          writeFileSync(`${file}.asc.${hash}`, createHash(hash).update(signature).digest("hex"));
      }
      for (const hash of ["md5", "sha1", "sha256", "sha512"])
        writeFileSync(
          `${file}.${hash}`,
          createHash(hash).update(content).digest("hex"),
        );
    }
  }
  return {
    options,
    directory: join(repository, "net/example/library", options.version),
  };
}

test("signed inventory keeps all Java attachments in a single deployment", (t) => {
  const { options } = publication(t);
  const report = inspectPublication(options);
  assert.equal(report.deployments, 1);
  assert.equal(report.fileCount, 100);
  assert.equal(report.modules[0].files, 50);
  assert.equal(report.modules[1].files, 50);
  assert.ok(report.bytes > 0);
  assert.equal(report.files.length, report.fileCount);
});

test("local unsigned report needs no credentials or signatures and ignores retained versions", (t) => {
  const { options } = publication(t, false);
  const old = join(options.repository, "net/example/library/old");
  mkdirSync(old);
  writeFileSync(join(old, "library-old.jar"), "old");
  const report = inspectPublication(options);
  assert.equal(report.fileCount, 50);
  assert.equal(report.signed, false);
});

test("local inventory resolves timestamped snapshots and ignores older snapshot builds", (t) => {
  const { options } = publication(t, false);
  for (const artifact of options.artifacts) {
    const directory = join(
      options.repository,
      "net/example",
      artifact,
      options.version,
    );
    const snapshot = `${options.version}-SNAPSHOT`;
    const target = join(options.repository, "net/example", artifact, snapshot);
    renameSync(directory, target);
    for (const file of readdirSync(target))
      renameSync(
        join(target, file),
        join(
          target,
          file.replace(options.version, `${options.version}-20261022.120000-2`),
        ),
      );
    writeFileSync(
      join(target, "maven-metadata.xml"),
      "<snapshot><timestamp>20261022.120000</timestamp><buildNumber>2</buildNumber></snapshot>",
    );
    writeFileSync(
      join(target, `${artifact}-${options.version}-20261021.120000-1.jar`),
      "old",
    );
  }
  const snapshotOptions = {
    ...options,
    version: `${options.version}-SNAPSHOT`,
  };
  assert.equal(inspectPublication(snapshotOptions).fileCount, 50);
  assert.throws(
    () => inspectPublication({ ...snapshotOptions, signed: true }),
    /cannot contain snapshots/,
  );
});

for (const missing of [
  ".jar",
  "-sources.jar",
  "-javadoc.jar",
  ".pom",
  ".module",
  ".jar.asc",
  ".jar.asc.sha256",
]) {
  test(`release inventory rejects missing ${missing}`, (t) => {
    const { options, directory } = publication(t);
    rmSync(join(directory, `library-${options.version}${missing}`));
    assert.throws(
      () => inspectPublication(options),
      /Missing publication file/,
    );
  });
}

test("release inventory rejects unexpected test fixture archives and checksum corruption", (t) => {
  const { options, directory } = publication(t);
  const extra = join(directory, `library-${options.version}-test-fixtures.jar`);
  writeFileSync(extra, "unexpected");
  assert.throws(
    () => inspectPublication(options),
    /Unexpected publication file/,
  );
  rmSync(extra);
  writeFileSync(
    join(directory, `library-${options.version}.jar.sha256`),
    "corrupt",
  );
  assert.throws(() => inspectPublication(options), /Checksum mismatch/);
});

test("release bundle rejects stale versions and repository metadata", (t) => {
  const { options } = publication(t);
  const metadata = join(
    options.repository,
    "net/example/library/maven-metadata.xml",
  );
  writeFileSync(metadata, "repository-managed");
  assert.throws(() => inspectPublication(options), /Unexpected bundle entry/);
  rmSync(metadata);
  const old = join(options.repository, "net/example/library/old");
  mkdirSync(old);
  writeFileSync(join(old, "library-old.jar"), "stale");
  assert.throws(() => inspectPublication(options), /Unexpected bundle entry/);
});

test("release bundle rejects symbolic links and duplicate artifact IDs", (t) => {
  const { options, directory } = publication(t);
  assert.throws(
    () => inspectPublication({ ...options, artifacts: ["library", "library"] }),
    /unique/,
  );
  const signature = join(directory, `library-${options.version}.jar.asc`);
  rmSync(signature);
  symlinkSync(join(directory, `library-${options.version}.pom.asc`), signature);
  assert.throws(() => inspectPublication(options), /Invalid publication file/);
});

const deploymentId = "12345678-1234-1234-1234-123456789abc";
function polling(states) {
  let time = 0;
  const calls = [];
  return {
    calls,
    options: {
      deploymentId,
      token: "test-token",
      password: "test-password",
      timeoutMs: 100,
      now: () => time,
      sleep: async () => {
        time += 10;
      },
      log: () => {},
      fetchRequest: async (url, options) => {
        calls.push({ url, ...options });
        const state = states.shift();
        if (state instanceof Error) throw state;
        return typeof state === "number"
          ? { status: state, ok: false }
          : {
              ok: true,
              status: 200,
              json: async () =>
                typeof state === "object"
                  ? state
                  : { deploymentId, deploymentState: state ?? "PUBLISHING" },
            };
      },
    },
  };
}

test("final success requires PUBLISHED, using the same authenticated deployment for every poll", async () => {
  const { options, calls } = polling(["VALIDATING", "PUBLISHING", "PUBLISHED"]);
  await waitForPublication(options);
  assert.equal(calls.length, 3);
  assert.ok(
    calls.every((call) => call.url.endsWith(`/status?id=${deploymentId}`)),
  );
  assert.ok(
    calls.every(
      (call) =>
        call.method === "POST" &&
        call.headers.Authorization.startsWith("Bearer "),
    ),
  );
});

test("temporary reads retry without uploading", async () => {
  const { options, calls } = polling([
    new Error("network"),
    503,
    429,
    "PUBLISHED",
  ]);
  await waitForPublication(options);
  assert.equal(calls.length, 4);
  assert.ok(calls.every((call) => call.url.includes("/status?")));
});

for (const failure of [
  new TypeError("terminated"),
  new DOMException("body timed out", "TimeoutError"),
]) {
  test(`temporary response body failure retries the same deployment: ${failure.name}`, async () => {
    const { options, calls } = polling(["PUBLISHED", "PUBLISHED"]);
    const fetchRequest = options.fetchRequest;
    options.fetchRequest = async (...args) => {
      const response = await fetchRequest(...args);
      if (calls.length === 1)
        response.json = async () => {
          throw failure;
        };
      return response;
    };
    await waitForPublication(options);
    assert.equal(calls.length, 2);
    assert.ok(
      calls.every((call) => call.url.endsWith(`/status?id=${deploymentId}`)),
    );
  });
}

test("malformed status JSON fails closed without publishing", async () => {
  const { options, calls } = polling(["PUBLISHED"]);
  const fetchRequest = options.fetchRequest;
  options.fetchRequest = async (...args) => {
    const response = await fetchRequest(...args);
    response.json = async () => {
      throw new SyntaxError("invalid JSON");
    };
    return response;
  };
  await assert.rejects(
    waitForPublication({ ...options, publish: true }),
    /Invalid Maven Central status response/,
  );
  assert.equal(calls.length, 1);
});

test("read-only recovery explains how to resume a VALIDATED deployment without writing", async () => {
  const { options, calls } = polling(["VALIDATED"]);
  await assert.rejects(
    waitForPublication(options),
    /Portal or resume with --publish.*do not re-upload/,
  );
  assert.equal(calls.length, 1);
  assert.ok(calls[0].url.includes("/status?"));
});

test("a status body completing after the deadline cannot trigger publication", async () => {
  const { options, calls } = polling(["VALIDATED"]);
  let time = 0;
  options.now = () => time;
  const fetchRequest = options.fetchRequest;
  options.fetchRequest = async (...args) => {
    const response = await fetchRequest(...args);
    const json = response.json;
    response.json = async () => {
      time = options.timeoutMs;
      return json();
    };
    return response;
  };
  await assert.rejects(
    waitForPublication({ ...options, publish: true }),
    /Timed out/,
  );
  assert.equal(calls.length, 1);
  assert.ok(calls[0].url.includes("/status?"));
});

for (const acknowledgement of [
  204,
  new TypeError("lost acknowledgement"),
  503,
  409,
  429,
]) {
  test(`upload-complete orchestration retries status then publishes the existing deployment once (${String(acknowledgement)})`, async () => {
    const { options, calls } = polling([
      new TypeError("first status failed"),
      "VALIDATING",
      "VALIDATED",
      "VALIDATED",
      "PUBLISHING",
      "PUBLISHED",
    ]);
    const fetchRequest = options.fetchRequest;
    options.fetchRequest = async (url, request) => {
      if (!url.includes("/deployment/")) return fetchRequest(url, request);
      calls.push({ url, ...request });
      if (acknowledgement instanceof Error) throw acknowledgement;
      return { status: acknowledgement };
    };
    await waitForPublication({ ...options, publish: true });
    assert.equal(
      calls.filter((call) => call.url.endsWith(`/deployment/${deploymentId}`))
        .length,
      1,
    );
    assert.ok(calls.every((call) => !call.url.includes("/upload")));
  });
}

for (const code of [401, 403]) {
  test(`publication permission failure ${code} is not retried`, async () => {
    const { options, calls } = polling(["VALIDATED"]);
    const fetchRequest = options.fetchRequest;
    options.fetchRequest = async (url, request) => {
      if (!url.includes("/deployment/")) return fetchRequest(url, request);
      calls.push({ url, ...request });
      return { status: code };
    };
    await assert.rejects(
      waitForPublication({ ...options, publish: true }),
      /Maven Central publish rejected/,
    );
    assert.equal(calls.length, 2);
  });
}

test("an unconfirmed publication remaining VALIDATED times out without posting again", async () => {
  const { options, calls } = polling([]);
  options.fetchRequest = async (url, request) => {
    calls.push({ url, ...request });
    return url.includes("/deployment/")
      ? { status: 503 }
      : {
          ok: true,
          status: 200,
          json: async () => ({ deploymentId, deploymentState: "VALIDATED" }),
        };
  };
  await assert.rejects(
    waitForPublication({ ...options, publish: true }),
    /last state: VALIDATED.*do not re-upload/,
  );
  assert.equal(
    calls.filter((call) => call.url.includes("/deployment/")).length,
    1,
  );
});

for (const state of [
  "FAILED",
  "UNKNOWN",
  401,
  403,
  { deploymentId: "different", deploymentState: "PUBLISHED" },
]) {
  test(`fails closed on ${JSON.stringify(state)}`, async () => {
    const { options, calls } = polling([state]);
    await assert.rejects(waitForPublication(options));
    assert.equal(calls.length, 1);
  });
}

test("publication timeout reports the existing ID and does not retry an upload", async () => {
  const { options, calls } = polling([]);
  await assert.rejects(
    waitForPublication(options),
    /Check the Portal.*do not re-upload/,
  );
  assert.equal(calls.length, 10);
});

test("invalid deployment IDs and missing credentials cause no requests", async () => {
  const { options, calls } = polling([]);
  await assert.rejects(
    waitForPublication({ ...options, deploymentId: "invalid" }),
  );
  await assert.rejects(waitForPublication({ ...options, password: "" }));
  assert.equal(calls.length, 0);
});

test("signature checksum corruption is rejected", (t) => {
  const { options, directory } = publication(t);
  writeFileSync(join(directory, `library-${options.version}.jar.asc.sha256`), "corrupt");
  assert.throws(() => inspectPublication(options), /Checksum mismatch/);
});

test("local publication graph is unsigned and restricted to the six release modules", () => {
  const root = fileURLToPath(new URL("../", import.meta.url));
  const graph = execFileSync("./gradlew", ["publishReleaseToLocalRepository", "--dry-run", "--console=plain"], {
    cwd: root, encoding: "utf8", timeout: 120000,
  });
  assert.doesNotMatch(graph, /:signMavenJavaPublication|:verifyReleaseCredentials|:verifyReleaseSigningCredentials|:2\.uploadDeploymentDir/);
  const publications = graph.split(/\r?\n/).filter((line) => line.endsWith(":publishMavenJavaPublicationToConsumerRepository SKIPPED"));
  assert.equal(publications.length, 6);
  assert.ok(publications.every((line) => !line.includes("integration-test") && !line.includes("database-test:")));
});

for (const [task, properties, message] of [
  ["verifyMavenCentralReleaseMode", [], /requires -Pmuyun.mavenCentralRelease=true/],
  ["verifyReleaseTagVersion", ["-Prelease.tag=v0.0.0"], /does not match project version/],
]) {
  test(`${task} rejects invalid release intent`, () => {
    const root = fileURLToPath(new URL("../", import.meta.url));
    assert.throws(() => execFileSync("./gradlew", [task, ...properties, "--console=plain"], {
      cwd: root, encoding: "utf8", timeout: 120000, stdio: "pipe",
    }), (error) => {
      assert.match(error.stderr, message);
      return true;
    });
  });
}

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { setTimeout } from "node:timers/promises";

/** Manage an existing deployment only; never upload or create another release event. */
export async function waitForPublication({
  deploymentId,
  token,
  password,
  publish = false,
  timeoutMs = 15 * 60 * 1000,
  fetchRequest = fetch,
  sleep = setTimeout,
  now = Date.now,
  log = console.log,
}) {
  if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(deploymentId))
    throw new Error("Missing or invalid Maven Central deployment ID");
  if (!token || !password)
    throw new Error(
      "Maven Central status check requires publishing credentials",
    );
  const authorization = `Bearer ${Buffer.from(`${token}:${password}`).toString("base64")}`;
  const deadline = now() + timeoutMs;
  let lastState = "unknown";
  let publishAttempted = false;
  const request = (path) =>
    fetchRequest(`https://central.sonatype.com/api/v1/publisher/${path}`, {
      method: "POST",
      headers: { Authorization: authorization },
      signal: AbortSignal.timeout(
        Math.max(1, Math.min(30000, deadline - now())),
      ),
    });
  while (now() < deadline) {
    let response;
    let status;
    try {
      response = await request(`status?id=${deploymentId}`);
      if (response.ok) status = await response.json();
    } catch (cause) {
      // Transport failures can also happen after headers, while reading the body.
      if (cause instanceof SyntaxError)
        throw new Error("Invalid Maven Central status response", { cause });
      response = undefined;
      log(
        "Maven Central status temporarily unavailable; retrying the same deployment",
      );
    }
    if (response) {
      if (response.status === 429 || response.status >= 500) {
        log(
          `Maven Central status returned HTTP ${response.status}; retrying the same deployment`,
        );
      } else {
        if (!response.ok)
          throw new Error(
            `Maven Central status rejected: HTTP ${response.status}`,
          );
        if (status?.deploymentId !== deploymentId)
          throw new Error("Maven Central deployment identity mismatch");
        lastState = status.deploymentState;
        log(`Maven Central deployment ${deploymentId}: ${lastState}`);
        if (now() >= deadline) break;
        if (lastState === "PUBLISHED") return;
        if (lastState === "VALIDATED") {
          if (!publish)
            throw new Error(
              `Deployment ${deploymentId} is VALIDATED. Publish it in the Portal or resume with --publish; do not re-upload.`,
            );
          if (!publishAttempted) {
            // An uncertain acknowledgement must be reconciled by status reads, not a blind POST retry.
            publishAttempted = true;
            let acknowledgement;
            try {
              acknowledgement = await request(`deployment/${deploymentId}`);
            } catch {
              log(
                "Maven Central publish acknowledgement unavailable; checking the same deployment",
              );
            }
            if (
              acknowledgement &&
              acknowledgement.status !== 204 &&
              acknowledgement.status !== 409 &&
              acknowledgement.status !== 429 &&
              acknowledgement.status < 500
            )
              throw new Error(
                `Maven Central publish rejected: HTTP ${acknowledgement.status}`,
              );
          }
        }
        if (
          !["PENDING", "VALIDATING", "VALIDATED", "PUBLISHING"].includes(
            lastState,
          )
        )
          throw new Error(
            `Maven Central deployment did not publish: ${lastState}`,
          );
      }
    }
    await sleep(Math.max(0, Math.min(10000, deadline - now())));
  }
  throw new Error(
    `Timed out waiting for deployment ${deploymentId} (last state: ${lastState}). Check the Portal before retrying; do not re-upload.`,
  );
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [idFile, flag, ...extra] = process.argv.slice(2);
  if (!idFile || (flag && flag !== "--publish") || extra.length)
    throw new Error(
      "Usage: node scripts/wait-maven-central.mjs <deployment-id-file> [--publish]",
    );
  const deploymentId = readFileSync(idFile, "utf8").trim();
  await waitForPublication({
    deploymentId,
    token: process.env.SONATYPE_TOKEN,
    password: process.env.SONATYPE_PASSWORD,
    publish: flag === "--publish",
  });
}

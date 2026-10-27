#!/usr/bin/env bash
set -euo pipefail
# Only swap the controller; tests and dependency setup are identical before/after.
controller=platform/forge/1.20.1/src/main/java/cn/alini/trueuuid/server/ServerLoginController.java
saved=$(mktemp)
cp "$controller" "$saved"
trap 'cp "$saved" "$controller"; rm -f "$saved"' EXIT
git show 73fd511:"$controller" > "$controller"
mkdir -p regression-results
if ./gradlew -Ptrueuuid.onlyTarget=forge-1.20.1 :platform:forge-1.20.1:test --tests '*RejectedLoginRegressionTest' --no-daemon; then
  echo 'Unmodified upstream unexpectedly passed the regressions' >&2
  exit 1
fi
cp platform/forge/1.20.1/build/test-results/test/TEST-cn.alini.trueuuid.server.RejectedLoginRegressionTest.xml regression-results/upstream.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('regression-results/upstream.xml').getroot()
failed = {t.attrib['name'].split('(')[0] for t in root.findall('testcase') if t.find('failure') is not None}
required = {
    'deniedOfflineAnswerCannotReachPlayerCreationOrSaveDuringAsyncClose',
    'externalDisconnectBeforeAnswerIsTerminal',
    'transportClosureWithoutListenerCallbackIsTerminal',
    'delayedSuccessfulVerifierCannotReviveDisconnectedSession',
    'migrationDeclineAndTimeoutNeverAccept',
    'migrationTimeoutCannotFallThroughSameTick',
    'disconnectedMigrationKeepsRetryBlockedUntilWorkerActuallyFinishes',
    'nettyAnswerIsProcessedOnServerThreadAndCannotOvertakeDisconnect',
}
assert int(root.attrib['errors']) == 0, root.attrib
assert required <= failed, (required - failed, failed)
print('Verified upstream assertion failures:', ', '.join(sorted(failed)))
PY

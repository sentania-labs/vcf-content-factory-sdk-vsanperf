# Test-only stubs

`com/vcfcf/adapter/json/SimpleJson.java` is a verbatim copy of the framework
class (factory `src/vcfcf_managementpacks/adapter_framework/src/com/vcfcf/adapter/json/SimpleJson.java`,
sha256 prefix `501451fe843d426a`, 2026-11-06). It has no dependency, so the
SDK-free adapter classes that parse JSON compile and run in
`ci/run_java_tests.sh` without the framework jar. It never ships:
`build-sdk` compiles `src/` only, against the real `vcfcf-adapter-base.jar`.
Refresh the copy when the framework class changes.

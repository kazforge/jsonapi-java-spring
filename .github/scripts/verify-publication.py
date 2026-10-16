"""Verify generated consumer metadata and production SBOM without credentials."""

import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path("jsonapi-java-spring-webmvc/build")
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
pom = ET.parse(root / "publications/mavenJava/pom-default.xml").getroot()
assert pom.findtext("m:groupId", namespaces=ns) == "com.kazforge"
assert pom.findtext("m:artifactId", namespaces=ns) == "jsonapi-java-spring-webmvc"
expected = {
    ("com.kazforge", "jsonapi-java-api"): "0.4.0",
    ("com.kazforge", "jsonapi-java-query"): "0.4.0",
    ("org.springframework", "spring-webmvc"): "7.0.0",
    ("jakarta.servlet", "jakarta.servlet-api"): "6.1.0",
}
deps = {}
for dep in pom.findall("m:dependencies/m:dependency", ns):
    def text(name):
        return dep.findtext("m:" + name, namespaces=ns)
    assert text("scope") == "compile"
    deps[(text("groupId"), text("artifactId"))] = text("version")
assert deps == expected, deps
assert pom.find("m:dependencyManagement", ns) is None
for field in ("name", "description", "url", "licenses", "developers", "scm"):
    assert pom.find("m:" + field, ns) is not None, field
metadata = json.loads((root / "publications/mavenJava/module.json").read_text())
for variant in metadata["variants"]:
    if variant["name"] in ("apiElements", "runtimeElements"):
        actual = {}
        for dep in variant["dependencies"]:
            assert set(dep["version"]) == {"requires"}, dep
            actual[(dep["group"], dep["module"])] = dep["version"]["requires"]
        assert actual == expected, actual
        assert not variant.get("dependencyConstraints")
bom = json.loads((root / "reports/release/bom.json").read_text())
assert bom["bomFormat"] == "CycloneDX"
components = bom["components"]
assert any(c["name"] == "jsonapi-java-api" and c["version"] == "0.4.0" for c in components)
assert any(c["name"] == "jsonapi-java-query" and c["version"] == "0.4.0" for c in components)
assert not any("jackson" in c["name"].lower() or "junit" in c["name"].lower() for c in components)
print("Publication metadata and production SBOM verified")

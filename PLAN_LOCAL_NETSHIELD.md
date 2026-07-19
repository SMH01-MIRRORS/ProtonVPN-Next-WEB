# Local NetShield implementation plan

- [x] Study official Proton VPN NetShield levels, statistics UI, settings flow, and ProtonCore icon assets.
- [x] Add persistent local NetShield level and rule-list update metadata.
- [x] Download, validate, atomically replace, and generate amnezia-box source rule sets.
- [x] Count blocked ads and trackers locally and estimate saved bandwidth without storing queried domains.
- [x] Add official-style statistics to the phone dashboard and a compact map overlay on tablet/desktop.
- [x] Add a NetShield settings entry and screen with level selection and manual list updates.
- [x] Add focused tests and run a debug build.
- [x] Fix URLhaus malware hosts parsing for tab-separated entries.

## Levels

1. Disabled
2. Malware blocking
3. Malware, ads, and trackers
4. Malware, ads, trackers, and adult content

## Privacy and statistics

Only aggregate counters are kept for the active VPN session. Queried domain names are never persisted. Saved bytes are explicitly presented as an estimate because blocked responses are not downloaded and their exact size cannot be measured.

## Follow-up: preflight connection sequencing

- [x] Reproduce and map the reconnect/DNS race while an existing VPN service is active.
- [x] Add an underlying-network preflight that resolves and validates the endpoint before starting the VPN service.
- [x] Ensure cancellation during disconnect is treated as expected and is not reported as a connection failure.
- [x] Add regression tests, run focused tests and a debug build, then commit.

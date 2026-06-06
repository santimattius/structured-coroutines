# Security Policy

## Supported Versions

Security fixes are applied to the latest release line. Older versions may not
receive patches unless noted in release notes.

| Version | Supported |
| ------- | --------- |
| 1.x     | ✅         |
| < 1.0   | ❌         |

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly.

**Do not open a public GitHub issue** for security-sensitive findings.

Instead, use one of these channels:

1. **[GitHub Private Vulnerability Reporting](https://github.com/santimattius/structured-coroutines/security/advisories/new)** (preferred)
2. Email the maintainer via GitHub profile contact, or open a minimal issue asking for a private channel if neither option above is available.

Include as much detail as possible:

- Description of the vulnerability and potential impact
- Steps to reproduce or a proof of concept
- Affected module(s) and version(s)
- Suggested fix, if you have one

## What to Expect

- **Acknowledgment** within 5 business days
- **Initial assessment** within 10 business days
- **Status updates** as the issue is triaged and fixed
- **Coordinated disclosure** — we will agree on a release timeline before publishing details

## Scope

This policy covers the Structured Coroutines repository and its published
artifacts (compiler plugin, Gradle plugin, Detekt rules, Lint rules, IntelliJ
plugin).

General coroutine misuse detected by these tools is intentional static analysis
behavior, not a security defect. Reports about rule false positives or feature
requests should use regular [issues](https://github.com/santimattius/structured-coroutines/issues).

## Safe Harbor

We support good-faith security research. We will not pursue legal action against
researchers who follow this policy and avoid privacy violations, data destruction,
or service disruption.

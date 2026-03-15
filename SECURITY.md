# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in NorthPost Parser, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please use [GitHub's private vulnerability reporting](../../security/advisories/new) to submit your report. You will receive a response acknowledging your report within 72 hours.

## Scope

NorthPost Parser is a pure parsing library with no network access, file system writes, or external service dependencies. The primary security consideration is denial-of-service via crafted input strings that could cause excessive parsing time or memory usage.

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
